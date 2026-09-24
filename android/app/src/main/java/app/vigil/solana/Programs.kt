package app.vigil.solana

import java.security.MessageDigest

object Programs {
    val SYSTEM = PublicKey.of("11111111111111111111111111111111")
    val TOKEN = PublicKey.of("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
    val TOKEN_2022 = PublicKey.of("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb")
    val ASSOCIATED_TOKEN = PublicKey.of("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL")
}

object SystemProgram {
    fun transfer(from: PublicKey, to: PublicKey, lamports: Long) = Instruction(
        Programs.SYSTEM,
        listOf(AccountMeta.signer(from, writable = true), AccountMeta.writable(to)),
        BorshWriter().u32(2).u64(lamports).toByteArray(),
    )
}

object TokenProgram {
    fun associatedTokenAddress(owner: PublicKey, mint: PublicKey, tokenProgram: PublicKey = Programs.TOKEN): PublicKey =
        PublicKey.findProgramAddress(
            listOf(owner.bytes, tokenProgram.bytes, mint.bytes),
            Programs.ASSOCIATED_TOKEN,
        ).first

    fun createAssociatedTokenAccountIdempotent(
        payer: PublicKey,
        owner: PublicKey,
        mint: PublicKey,
        tokenProgram: PublicKey,
    ) = Instruction(
        Programs.ASSOCIATED_TOKEN,
        listOf(
            AccountMeta.signer(payer, writable = true),
            AccountMeta.writable(associatedTokenAddress(owner, mint, tokenProgram)),
            AccountMeta.readonly(owner),
            AccountMeta.readonly(mint),
            AccountMeta.readonly(Programs.SYSTEM),
            AccountMeta.readonly(tokenProgram),
        ),
        byteArrayOf(1),
    )

    fun transferChecked(
        source: PublicKey,
        mint: PublicKey,
        destination: PublicKey,
        authority: PublicKey,
        amount: Long,
        decimals: Int,
        tokenProgram: PublicKey,
    ) = Instruction(
        tokenProgram,
        listOf(
            AccountMeta.writable(source),
            AccountMeta.readonly(mint),
            AccountMeta.writable(destination),
            AccountMeta.signer(authority),
        ),
        BorshWriter().u8(12).u64(amount).u8(decimals).toByteArray(),
    )
}

internal fun anchorDiscriminator(namespace: String, name: String): ByteArray =
    MessageDigest.getInstance("SHA-256").digest("$namespace:$name".toByteArray()).copyOf(8)
