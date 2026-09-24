package app.vigil.solana

import app.vigil.BuildConfig

data class Heir(val wallet: PublicKey, val shareBps: Int)

/** On-chain vault state, decoded from the program account. Layout mirrors `programs/vigil/src/state.rs`. */
data class VaultAccount(
    val address: PublicKey,
    val owner: PublicKey,
    val pulseKey: PublicKey,
    val interval: Long,
    val grace: Long,
    val createdAt: Long,
    val lastPulse: Long,
    val streak: Int,
    val bestStreak: Int,
    val totalPulses: Long,
    val releasedAt: Long,
    val heirs: List<Heir>,
    val bump: Int,
    val lamports: Long,
) {
    val dueAt: Long get() = lastPulse + interval
    val deadline: Long get() = lastPulse + interval + grace
    val isReleased: Boolean get() = releasedAt != 0L

    fun phase(now: Long): VaultPhase = when {
        isReleased -> VaultPhase.Released
        now > deadline -> VaultPhase.Expired
        now > dueAt -> VaultPhase.Overdue
        else -> VaultPhase.Alive
    }
}

enum class VaultPhase { Alive, Overdue, Expired, Released }

object VigilProgram {
    val ID: PublicKey = PublicKey.of(BuildConfig.VIGIL_PROGRAM_ID)

    const val ACCOUNT_SIZE = 300
    const val MAX_HEIRS = 5
    private const val HEIRS_OFFSET = 129
    private const val HEIR_SIZE = 34
    private val VAULT_SEED = "vault".toByteArray()
    private val ACCOUNT_DISCRIMINATOR = anchorDiscriminator("account", "Vault")

    fun vaultAddress(owner: PublicKey): PublicKey =
        PublicKey.findProgramAddress(listOf(VAULT_SEED, owner.bytes), ID).first

    /** Byte offset of heir slot `index`, for `getProgramAccounts` memcmp filters. */
    fun heirOffset(index: Int): Int = HEIRS_OFFSET + index * HEIR_SIZE

    fun decode(address: PublicKey, account: AccountData): VaultAccount? {
        val data = account.data
        if (data.size < ACCOUNT_SIZE || !data.copyOf(8).contentEquals(ACCOUNT_DISCRIMINATOR)) return null
        val r = BorshReader(data, 8)
        val owner = r.pubkey()
        val pulseKey = r.pubkey()
        val interval = r.i64()
        val grace = r.i64()
        val createdAt = r.i64()
        val lastPulse = r.i64()
        val streak = r.u32().toInt()
        val bestStreak = r.u32().toInt()
        val totalPulses = r.u64()
        val releasedAt = r.i64()
        val heirCount = r.u8()
        val slots = List(MAX_HEIRS) { Heir(r.pubkey(), r.u16()) }
        val bump = r.u8()
        return VaultAccount(
            address, owner, pulseKey, interval, grace, createdAt, lastPulse,
            streak, bestStreak, totalPulses, releasedAt,
            slots.take(heirCount.coerceAtMost(MAX_HEIRS)), bump, account.lamports,
        )
    }

    private fun ix(name: String, accounts: List<AccountMeta>, args: BorshWriter.() -> Unit = {}) =
        Instruction(ID, accounts, BorshWriter().bytes(anchorDiscriminator("global", name)).apply(args).toByteArray())

    private fun BorshWriter.heirs(heirs: List<Heir>) = apply {
        u32(heirs.size)
        heirs.forEach { pubkey(it.wallet); u16(it.shareBps) }
    }

    fun createVault(owner: PublicKey, interval: Long, grace: Long, pulseKey: PublicKey, heirs: List<Heir>) = ix(
        "create_vault",
        listOf(
            AccountMeta.signer(owner, writable = true),
            AccountMeta.writable(vaultAddress(owner)),
            AccountMeta.readonly(Programs.SYSTEM),
        ),
    ) { i64(interval); i64(grace); pubkey(pulseKey); heirs(heirs) }

    fun pulse(authority: PublicKey, vault: PublicKey) = ix(
        "pulse",
        listOf(AccountMeta.signer(authority), AccountMeta.writable(vault)),
    )

    private fun ownerOnly(owner: PublicKey) = listOf(
        AccountMeta.signer(owner, writable = true),
        AccountMeta.writable(vaultAddress(owner)),
    )

    fun configure(owner: PublicKey, interval: Long, grace: Long, heirs: List<Heir>) =
        ix("configure", ownerOnly(owner)) { i64(interval); i64(grace); heirs(heirs) }

    fun rotatePulseKey(owner: PublicKey, pulseKey: PublicKey) =
        ix("rotate_pulse_key", ownerOnly(owner)) { pubkey(pulseKey) }

    fun deposit(depositor: PublicKey, vault: PublicKey, lamports: Long) = ix(
        "deposit",
        listOf(
            AccountMeta.signer(depositor, writable = true),
            AccountMeta.writable(vault),
            AccountMeta.readonly(Programs.SYSTEM),
        ),
    ) { u64(lamports) }

    fun withdraw(owner: PublicKey, lamports: Long) = ix("withdraw", ownerOnly(owner)) { u64(lamports) }

    fun withdrawToken(
        owner: PublicKey,
        mint: PublicKey,
        tokenProgram: PublicKey,
        amount: Long,
    ): Instruction {
        val vault = vaultAddress(owner)
        return ix(
            "withdraw_token",
            listOf(
                AccountMeta.signer(owner, writable = true),
                AccountMeta.writable(vault),
                AccountMeta.readonly(mint),
                AccountMeta.writable(TokenProgram.associatedTokenAddress(vault, mint, tokenProgram)),
                AccountMeta.writable(TokenProgram.associatedTokenAddress(owner, mint, tokenProgram)),
                AccountMeta.readonly(tokenProgram),
            ),
        ) { u64(amount) }
    }

    fun closeVault(owner: PublicKey) = ix("close_vault", ownerOnly(owner))

    fun releaseSol(caller: PublicKey, vault: VaultAccount) = ix(
        "release_sol",
        listOf(AccountMeta.signer(caller), AccountMeta.writable(vault.address)) +
            vault.heirs.map { AccountMeta.writable(it.wallet) },
    )

    fun releaseToken(caller: PublicKey, vault: VaultAccount, mint: PublicKey, tokenProgram: PublicKey) = ix(
        "release_token",
        listOf(
            AccountMeta.signer(caller),
            AccountMeta.writable(vault.address),
            AccountMeta.readonly(mint),
            AccountMeta.writable(TokenProgram.associatedTokenAddress(vault.address, mint, tokenProgram)),
            AccountMeta.readonly(tokenProgram),
        ) + vault.heirs.map {
            AccountMeta.writable(TokenProgram.associatedTokenAddress(it.wallet, mint, tokenProgram))
        },
    )
}
