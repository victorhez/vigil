# Security notes

Vigil has not been audited. It runs on devnet. This document describes the threat model, the
invariants the program enforces, and the limitations we know about.

## Assets and actors

- **Vault PDA** `["vault", owner]` holds SOL in its own lamports and owns associated token accounts for SPL and Token-2022 assets.
- **Owner wallet** has full control while the vault is live.
- **Pulse key** is an Ed25519 key generated on the phone. It can only call `pulse`.
- **Heirs** are up to five wallets with basis-point shares.
- **Callers** can be anyone. Release is permissionless.

## Invariants

1. Only the owner can move funds out of a live vault (`has_one = owner` on every withdrawal path).
2. The pulse key can never move funds, and cannot check in once the vault has expired.
3. Nothing can be released before `now > last_pulse + interval + grace`.
4. Released funds go only to the heir accounts recorded on-chain, in their recorded proportions.
   SOL recipients must match the heir wallets exactly. Token recipients must be token accounts
   of the right mint, owned by the right heir, under the right token program.
5. Shares always sum to exactly 10,000 bps, with no duplicate, default or self-referential heirs.
6. The vault never drops below its rent-exempt minimum through `withdraw` or `release_sol`.
7. After the first release the vault is sealed. The owner can no longer pulse, configure, withdraw or close it.

## Threats considered

| Threat | Outcome |
|---|---|
| Phone stolen, attacker passes biometrics | Can keep the vault alive, cannot take funds. The owner rotates the pulse key from their wallet. |
| Phone found after the owner dies | The pulse key is refused once the vault has expired, so heirs can still claim. |
| Malicious release caller | Pays the fee. Destinations and amounts are fixed by the program. |
| Heir list swapped in the release transaction | Rejected with `HeirMismatch`. |
| Owner loses their wallet | If they named a backup wallet of their own as heir, funds return to it after expiry. |
| Pulse key extracted from the device | The seed is sealed with AES-GCM under an Android Keystore key that requires biometric or device-credential authentication. It is excluded from cloud backup and device transfer. |

## Known limitations

- **Clock source.** Deadlines use the cluster `Clock` sysvar, which can drift slightly from wall-clock time. Grace periods should be measured in hours or days, not seconds.
- **Revive race.** An owner who reappears after expiry can revive the vault with their wallet, but only if no one has released it yet. This is intentional: a release is final.
- **New heir wallets and SOL.** A release credits heir wallets directly. If an heir's wallet doesn't exist yet and their share of the SOL is below the rent-exempt minimum (~0.00089 SOL), the transaction fails. Name wallets that already exist, or keep each share comfortably above that amount. A future version will route dust to the largest share.
- **Token-2022 extensions.** Transfer fees reduce what heirs receive. Mints with transfer hooks are not supported.
- **Closing with tokens.** `close_vault` does not inspect token accounts. The app withdraws every token balance in the same transaction before closing.
- **Pulse key in memory.** Android Keystore has no Ed25519 signing, so the seed is unwrapped into app memory for the duration of a check-in. The decrypted buffer is zeroed after the key object is built; the key object itself lives until garbage collection.
- **One vault per owner.**

## Reporting

Please report vulnerabilities privately through GitHub Security Advisories on this repository.
