/**
 * Deploys or upgrades target/deploy/vigil.so through the upgradeable BPF loader,
 * using only @solana/web3.js. Equivalent to `solana program deploy`, useful where the
 * Solana CLI is unavailable.
 *
 *   VIGIL_PAYER=../.keys/deployer.json VIGIL_PROGRAM_KEYPAIR=../.keys/vigil-program.json npm run deploy
 *
 * The payer becomes the upgrade authority. Re-running against an existing program performs an upgrade.
 */
import { readFileSync } from "node:fs";
import {
  Connection,
  Keypair,
  LAMPORTS_PER_SOL,
  PublicKey,
  SYSVAR_CLOCK_PUBKEY,
  SYSVAR_RENT_PUBKEY,
  SystemProgram,
  Transaction,
  TransactionInstruction,
  sendAndConfirmTransaction,
} from "@solana/web3.js";

const LOADER = new PublicKey("BPFLoaderUpgradeab1e11111111111111111111111");
const RPC = process.env.VIGIL_RPC ?? "https://api.devnet.solana.com";
const SO_PATH = process.env.VIGIL_SO ?? "target/deploy/vigil.so";
const CHUNK = 950;
const PARALLEL = 16;

const loadKeypair = (path: string) => Keypair.fromSecretKey(Uint8Array.from(JSON.parse(readFileSync(path, "utf8"))));
const payer = loadKeypair(process.env.VIGIL_PAYER ?? "../.keys/deployer.json");
const programKp = loadKeypair(process.env.VIGIL_PROGRAM_KEYPAIR ?? "../.keys/vigil-program.json");
const connection = new Connection(RPC, "confirmed");

const u32 = (n: number) => { const b = Buffer.alloc(4); b.writeUInt32LE(n); return b; };
const u64 = (n: number | bigint) => { const b = Buffer.alloc(8); b.writeBigUInt64LE(BigInt(n)); return b; };

const BUFFER_HEADER = 37;      // enum tag (4) + Option<Pubkey> authority (1 + 32)
const PROGRAM_SIZE = 36;       // enum tag (4) + programdata address (32)
const PROGRAMDATA_HEADER = 45; // enum tag (4) + slot (8) + Option<Pubkey> authority (1 + 32)

const initializeBuffer = (buffer: PublicKey, authority: PublicKey) =>
  new TransactionInstruction({
    programId: LOADER,
    keys: [{ pubkey: buffer, isSigner: false, isWritable: true }, { pubkey: authority, isSigner: false, isWritable: false }],
    data: u32(0),
  });

const write = (buffer: PublicKey, authority: PublicKey, offset: number, bytes: Buffer) =>
  new TransactionInstruction({
    programId: LOADER,
    keys: [{ pubkey: buffer, isSigner: false, isWritable: true }, { pubkey: authority, isSigner: true, isWritable: false }],
    data: Buffer.concat([u32(1), u32(offset), u64(bytes.length), bytes]),
  });

const deploy = (program: PublicKey, programData: PublicKey, buffer: PublicKey, maxLen: number) =>
  new TransactionInstruction({
    programId: LOADER,
    keys: [
      { pubkey: payer.publicKey, isSigner: true, isWritable: true },
      { pubkey: programData, isSigner: false, isWritable: true },
      { pubkey: program, isSigner: false, isWritable: true },
      { pubkey: buffer, isSigner: false, isWritable: true },
      { pubkey: SYSVAR_RENT_PUBKEY, isSigner: false, isWritable: false },
      { pubkey: SYSVAR_CLOCK_PUBKEY, isSigner: false, isWritable: false },
      { pubkey: SystemProgram.programId, isSigner: false, isWritable: false },
      { pubkey: payer.publicKey, isSigner: true, isWritable: false },
    ],
    data: Buffer.concat([u32(2), u64(maxLen)]),
  });

const upgrade = (program: PublicKey, programData: PublicKey, buffer: PublicKey) =>
  new TransactionInstruction({
    programId: LOADER,
    keys: [
      { pubkey: programData, isSigner: false, isWritable: true },
      { pubkey: program, isSigner: false, isWritable: true },
      { pubkey: buffer, isSigner: false, isWritable: true },
      { pubkey: payer.publicKey, isSigner: false, isWritable: true },
      { pubkey: SYSVAR_RENT_PUBKEY, isSigner: false, isWritable: false },
      { pubkey: SYSVAR_CLOCK_PUBKEY, isSigner: false, isWritable: false },
      { pubkey: payer.publicKey, isSigner: true, isWritable: false },
    ],
    data: u32(3),
  });

async function main() {
  const so = readFileSync(SO_PATH);
  const program = programKp.publicKey;
  const [programData] = PublicKey.findProgramAddressSync([program.toBuffer()], LOADER);
  const existing = await connection.getAccountInfo(program);

  const bufferRent = await connection.getMinimumBalanceForRentExemption(BUFFER_HEADER + so.length);
  const dataRent = existing ? 0 : await connection.getMinimumBalanceForRentExemption(PROGRAMDATA_HEADER + so.length);
  const balance = await connection.getBalance(payer.publicKey);
  const needed = bufferRent + dataRent + 0.02 * LAMPORTS_PER_SOL;

  console.log(`program     ${program.toBase58()} (${existing ? "upgrade" : "new deploy"})`);
  console.log(`payer       ${payer.publicKey.toBase58()}  ${(balance / LAMPORTS_PER_SOL).toFixed(3)} SOL`);
  console.log(`binary      ${so.length} bytes, needs ~${(needed / LAMPORTS_PER_SOL).toFixed(3)} SOL`);
  if (balance < needed) throw new Error("payer balance too low");

  const buffer = Keypair.generate();
  await sendAndConfirmTransaction(connection, new Transaction().add(
    SystemProgram.createAccount({
      fromPubkey: payer.publicKey,
      newAccountPubkey: buffer.publicKey,
      lamports: bufferRent,
      space: BUFFER_HEADER + so.length,
      programId: LOADER,
    }),
    initializeBuffer(buffer.publicKey, payer.publicKey),
  ), [payer, buffer]);
  console.log(`buffer      ${buffer.publicKey.toBase58()}`);

  const offsets: number[] = [];
  for (let o = 0; o < so.length; o += CHUNK) offsets.push(o);
  let done = 0;
  const writeChunk = async (offset: number) => {
    for (let attempt = 0; ; attempt++) {
      try {
        await sendAndConfirmTransaction(
          connection,
          new Transaction().add(write(buffer.publicKey, payer.publicKey, offset, so.subarray(offset, offset + CHUNK))),
          [payer],
        );
        done++;
        if (done % 25 === 0 || done === offsets.length) process.stdout.write(`\rwriting     ${done}/${offsets.length}`);
        return;
      } catch (e) {
        if (attempt >= 5) throw e;
        await new Promise((r) => setTimeout(r, 1000 * (attempt + 1)));
      }
    }
  };
  for (let i = 0; i < offsets.length; i += PARALLEL) await Promise.all(offsets.slice(i, i + PARALLEL).map(writeChunk));
  console.log();

  const onChain = await connection.getAccountInfo(buffer.publicKey);
  if (!onChain || !onChain.data.subarray(BUFFER_HEADER).equals(so)) throw new Error("buffer contents do not match the binary");

  const sig = existing
    ? await sendAndConfirmTransaction(connection, new Transaction().add(upgrade(program, programData, buffer.publicKey)), [payer])
    : await sendAndConfirmTransaction(connection, new Transaction().add(
        SystemProgram.createAccount({
          fromPubkey: payer.publicKey,
          newAccountPubkey: program,
          lamports: await connection.getMinimumBalanceForRentExemption(PROGRAM_SIZE),
          space: PROGRAM_SIZE,
          programId: LOADER,
        }),
        deploy(program, programData, buffer.publicKey, so.length),
      ), [payer, programKp]);

  console.log(`${existing ? "upgraded" : "deployed"}    ${sig}`);
}

main().catch((e) => {
  console.error("\nFAILED:", e instanceof Error ? e.message : e);
  process.exit(1);
});
