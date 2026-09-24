/**
 * End-to-end test against a live cluster (devnet by default).
 *
 * Walks a vault through its whole life: create, deposit SOL and an SPL token, check in with the
 * device key, reject unauthorised and premature calls, expire, release to two heirs, and confirm
 * the vault is sealed afterwards.
 *
 *   VIGIL_FUNDER=../.keys/deployer.json npm run test:e2e
 */
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import {
  Connection,
  Keypair,
  LAMPORTS_PER_SOL,
  PublicKey,
  SendTransactionError,
  SystemProgram,
  Transaction,
  TransactionInstruction,
  sendAndConfirmTransaction,
} from "@solana/web3.js";
import {
  TOKEN_PROGRAM_ID,
  createMint,
  getAssociatedTokenAddressSync,
  getAccount,
  getOrCreateAssociatedTokenAccount,
  mintTo,
  createAssociatedTokenAccountIdempotentInstruction,
  createTransferCheckedInstruction,
} from "@solana/spl-token";

const RPC = process.env.VIGIL_RPC ?? "https://api.devnet.solana.com";
const PROGRAM_ID = new PublicKey(process.env.VIGIL_PROGRAM_ID ?? "VigddEZM9A4TuLmKFkY5qwVA5eCDDniEPJ4gmGQM512");
const FUNDER_PATH = process.env.VIGIL_FUNDER ?? "../.keys/deployer.json";

const connection = new Connection(RPC, "confirmed");
const funder = Keypair.fromSecretKey(Uint8Array.from(JSON.parse(readFileSync(FUNDER_PATH, "utf8"))));

// ---------------------------------------------------------------- encoding

const disc = (ns: string, name: string) => createHash("sha256").update(`${ns}:${name}`).digest().subarray(0, 8);
const i64 = (n: number | bigint) => { const b = Buffer.alloc(8); b.writeBigInt64LE(BigInt(n)); return b; };
const u64 = (n: number | bigint) => { const b = Buffer.alloc(8); b.writeBigUInt64LE(BigInt(n)); return b; };
const u32 = (n: number) => { const b = Buffer.alloc(4); b.writeUInt32LE(n); return b; };
const u16 = (n: number) => { const b = Buffer.alloc(2); b.writeUInt16LE(n); return b; };

type Heir = { wallet: PublicKey; shareBps: number };
const heirsArg = (heirs: Heir[]) =>
  Buffer.concat([u32(heirs.length), ...heirs.map((h) => Buffer.concat([h.wallet.toBuffer(), u16(h.shareBps)]))]);

const vaultPda = (owner: PublicKey) => PublicKey.findProgramAddressSync([Buffer.from("vault"), owner.toBuffer()], PROGRAM_ID)[0];

const ix = (name: string, keys: TransactionInstruction["keys"], args: Buffer = Buffer.alloc(0)) =>
  new TransactionInstruction({ programId: PROGRAM_ID, keys, data: Buffer.concat([disc("global", name), args]) });

const w = (pubkey: PublicKey) => ({ pubkey, isSigner: false, isWritable: true });
const r = (pubkey: PublicKey) => ({ pubkey, isSigner: false, isWritable: false });
const s = (pubkey: PublicKey, isWritable = false) => ({ pubkey, isSigner: true, isWritable });

function decodeVault(data: Buffer) {
  if (!data.subarray(0, 8).equals(disc("account", "Vault"))) throw new Error("not a Vault account");
  let o = 8;
  const key = () => { const k = new PublicKey(data.subarray(o, o + 32)); o += 32; return k; };
  const readI64 = () => { const v = data.readBigInt64LE(o); o += 8; return Number(v); };
  const owner = key(), pulseKey = key();
  const interval = readI64(), grace = readI64(), createdAt = readI64(), lastPulse = readI64();
  const streak = data.readUInt32LE(o); o += 4;
  const bestStreak = data.readUInt32LE(o); o += 4;
  const totalPulses = Number(data.readBigUInt64LE(o)); o += 8;
  const releasedAt = readI64();
  const heirCount = data[o]; o += 1;
  const heirs: Heir[] = [];
  for (let i = 0; i < 5; i++) { const wallet = key(); const shareBps = data.readUInt16LE(o); o += 2; if (i < heirCount) heirs.push({ wallet, shareBps }); }
  return { owner, pulseKey, interval, grace, createdAt, lastPulse, streak, bestStreak, totalPulses, releasedAt, heirs, bump: data[o] };
}

// ---------------------------------------------------------------- helpers

let passed = 0;
async function step(name: string, fn: () => Promise<void>) {
  process.stdout.write(`  • ${name} … `);
  await fn();
  passed++;
  console.log("ok");
}

async function send(signers: Keypair[], ...ixs: TransactionInstruction[]) {
  return sendAndConfirmTransaction(connection, new Transaction().add(...ixs), signers, { commitment: "confirmed" });
}

async function expectError(code: string, signers: Keypair[], ...ixs: TransactionInstruction[]) {
  try {
    await send(signers, ...ixs);
  } catch (e) {
    const logs = e instanceof SendTransactionError ? (await e.getLogs(connection)) ?? [] : [];
    const text = `${(e as Error).message}\n${logs.join("\n")}`;
    if (text.includes(code)) return;
    throw new Error(`expected ${code}, got:\n${text}`);
  }
  throw new Error(`expected ${code}, but the transaction succeeded`);
}

const sleep = (ms: number) => new Promise((res) => setTimeout(res, ms));

async function fetchVault(owner: PublicKey) {
  const info = await connection.getAccountInfo(vaultPda(owner), "confirmed");
  if (!info) throw new Error("vault missing");
  return { ...decodeVault(info.data), lamports: info.lamports };
}

// ---------------------------------------------------------------- scenario

async function main() {
  console.log(`Vigil e2e · ${RPC}\nprogram ${PROGRAM_ID.toBase58()}\n`);

  const owner = Keypair.generate();
  const pulse = Keypair.generate();
  const stranger = Keypair.generate();
  const heirA = Keypair.generate();
  const heirB = Keypair.generate();
  const vault = vaultPda(owner.publicKey);
  const INTERVAL = 60;
  const heirs: Heir[] = [
    { wallet: heirA.publicKey, shareBps: 6_000 },
    { wallet: heirB.publicKey, shareBps: 4_000 },
  ];

  await step("fund test wallets", async () => {
    await send(
      [funder],
      SystemProgram.transfer({ fromPubkey: funder.publicKey, toPubkey: owner.publicKey, lamports: 0.25 * LAMPORTS_PER_SOL }),
      SystemProgram.transfer({ fromPubkey: funder.publicKey, toPubkey: stranger.publicKey, lamports: 0.02 * LAMPORTS_PER_SOL }),
    );
  });

  await step("rejects shares that do not total 100%", async () => {
    await expectError("SharesMustTotal100", [owner],
      ix("create_vault", [s(owner.publicKey, true), w(vault), r(SystemProgram.programId)],
        Buffer.concat([i64(INTERVAL), i64(0), pulse.publicKey.toBuffer(), heirsArg([{ ...heirs[0], shareBps: 5_000 }, heirs[1]])])));
  });

  await step("creates the vault, funds the pulse key, deposits SOL", async () => {
    await send([owner],
      SystemProgram.transfer({ fromPubkey: owner.publicKey, toPubkey: pulse.publicKey, lamports: 0.01 * LAMPORTS_PER_SOL }),
      ix("create_vault", [s(owner.publicKey, true), w(vault), r(SystemProgram.programId)],
        Buffer.concat([i64(INTERVAL), i64(0), pulse.publicKey.toBuffer(), heirsArg(heirs)])),
      ix("deposit", [s(owner.publicKey, true), w(vault), r(SystemProgram.programId)], u64(0.1 * LAMPORTS_PER_SOL)),
    );
    const v = await fetchVault(owner.publicKey);
    if (!v.owner.equals(owner.publicKey) || !v.pulseKey.equals(pulse.publicKey)) throw new Error("bad keys");
    if (v.interval !== INTERVAL || v.heirs.length !== 2 || v.totalPulses !== 1 || v.streak !== 1) throw new Error("bad state");
  });

  const mintAuthority = funder;
  let mint: PublicKey;
  await step("deposits an SPL token into the vault's token account", async () => {
    mint = await createMint(connection, funder, mintAuthority.publicKey, null, 6);
    const ownerAta = await getOrCreateAssociatedTokenAccount(connection, funder, mint, owner.publicKey);
    await mintTo(connection, funder, mint, ownerAta.address, mintAuthority, 1_000_000_000n);
    const vaultAta = getAssociatedTokenAddressSync(mint, vault, true);
    await send([owner],
      createAssociatedTokenAccountIdempotentInstruction(owner.publicKey, vaultAta, vault, mint),
      createTransferCheckedInstruction(ownerAta.address, mint, vaultAta, owner.publicKey, 1_000_000_000n, 6),
    );
  });

  await step("device pulse key can check in", async () => {
    const before = await fetchVault(owner.publicKey);
    await sleep(1_500);
    await send([pulse], ix("pulse", [s(pulse.publicKey), w(vault)]));
    const after = await fetchVault(owner.publicKey);
    if (after.totalPulses !== before.totalPulses + 1 || after.lastPulse < before.lastPulse) throw new Error("pulse not recorded");
  });

  await step("strangers cannot check in", async () => {
    await expectError("UnauthorizedPulse", [stranger], ix("pulse", [s(stranger.publicKey), w(vault)]));
  });

  await step("pulse key cannot withdraw", async () => {
    await expectError("ConstraintHasOne", [pulse], ix("withdraw", [s(pulse.publicKey, true), w(vault)], u64(1)));
  });

  await step("release is refused while the owner is alive", async () => {
    await expectError("NotExpired", [stranger],
      ix("release_sol", [s(stranger.publicKey), w(vault), w(heirA.publicKey), w(heirB.publicKey)]));
  });

  await step("owner can withdraw, and it counts as a check-in", async () => {
    const before = await fetchVault(owner.publicKey);
    await send([owner], ix("withdraw", [s(owner.publicKey, true), w(vault)], u64(0.01 * LAMPORTS_PER_SOL)));
    const after = await fetchVault(owner.publicKey);
    if (after.lamports !== before.lamports - 0.01 * LAMPORTS_PER_SOL) throw new Error("withdraw amount");
    if (after.totalPulses !== before.totalPulses + 1) throw new Error("withdraw should pulse");
  });

  process.stdout.write(`  … waiting ${INTERVAL + 5}s for the vault to expire\n`);
  await sleep((INTERVAL + 5) * 1_000);

  await step("expired vault rejects the device key", async () => {
    await expectError("PulseKeyExpired", [pulse], ix("pulse", [s(pulse.publicKey), w(vault)]));
  });

  await step("release rejects a swapped heir account", async () => {
    await expectError("HeirMismatch", [stranger],
      ix("release_sol", [s(stranger.publicKey), w(vault), w(heirA.publicKey), w(stranger.publicKey)]));
  });

  await step("anyone can release SOL; heirs receive 60/40", async () => {
    const v = await fetchVault(owner.publicKey);
    const rent = await connection.getMinimumBalanceForRentExemption(300);
    const available = v.lamports - rent;
    await send([stranger], ix("release_sol", [s(stranger.publicKey), w(vault), w(heirA.publicKey), w(heirB.publicKey)]));
    const a = await connection.getBalance(heirA.publicKey, "confirmed");
    const b = await connection.getBalance(heirB.publicKey, "confirmed");
    const expectA = Math.floor((available * 6_000) / 10_000);
    if (a !== expectA || b !== available - expectA) throw new Error(`split ${a}/${b}, expected ${expectA}/${available - expectA}`);
    const after = await fetchVault(owner.publicKey);
    if (after.releasedAt === 0) throw new Error("released_at not set");
  });

  await step("anyone can release tokens; heirs receive 60/40", async () => {
    const program = TOKEN_PROGRAM_ID;
    const heirAtas = heirs.map((h) => getAssociatedTokenAddressSync(mint, h.wallet));
    await send([stranger],
      ...heirs.map((h, i) => createAssociatedTokenAccountIdempotentInstruction(stranger.publicKey, heirAtas[i], h.wallet, mint)),
      ix("release_token", [
        s(stranger.publicKey), w(vault), r(mint), w(getAssociatedTokenAddressSync(mint, vault, true)), r(program),
        ...heirAtas.map(w),
      ]),
    );
    const [a, b] = await Promise.all(heirAtas.map((ata) => getAccount(connection, ata, "confirmed")));
    if (a.amount !== 600_000_000n || b.amount !== 400_000_000n) throw new Error(`token split ${a.amount}/${b.amount}`);
  });

  await step("a released vault is sealed for its owner", async () => {
    await expectError("AlreadyReleased", [owner], ix("pulse", [s(owner.publicKey), w(vault)]));
    await expectError("AlreadyReleased", [owner], ix("withdraw", [s(owner.publicKey, true), w(vault)], u64(1)));
  });

  console.log(`\n${passed} checks passed.`);
}

main().catch((e) => {
  console.error("\nFAILED:", e instanceof Error ? e.message : e);
  process.exit(1);
});
