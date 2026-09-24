package app.vigil.data

import app.vigil.solana.Programs
import app.vigil.solana.PublicKey
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

const val LAMPORTS_PER_SOL = 1_000_000_000L

data class TokenInfo(val symbol: String, val name: String, val featured: Boolean = false)

object KnownTokens {
    val SKR: PublicKey = PublicKey.of("SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3")

    private val registry = mapOf(
        SKR to TokenInfo("SKR", "Solana Mobile", featured = true),
        PublicKey.of("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v") to TokenInfo("USDC", "USD Coin"),
        PublicKey.of("4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU") to TokenInfo("USDC", "USD Coin · devnet"),
        PublicKey.of("Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB") to TokenInfo("USDT", "Tether USD"),
        PublicKey.of("JUPyiwrYJFskUPiHa7hkeR8VUtAeFoSYbKedZNsDvCN") to TokenInfo("JUP", "Jupiter"),
        PublicKey.of("DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263") to TokenInfo("BONK", "Bonk"),
        PublicKey.of("jtojtomepa8beP8AuQc6eXt5FriJwfFMwQx2v2f9mCL") to TokenInfo("JTO", "Jito"),
    )

    fun info(mint: PublicKey): TokenInfo =
        registry[mint] ?: TokenInfo(mint.toBase58().take(4).uppercase(), "Token ${mint.short()}")
}

/** A balance held by a wallet or vault. `mint == null` means native SOL. */
data class Asset(
    val mint: PublicKey?,
    val symbol: String,
    val name: String,
    val amount: Long,
    val decimals: Int,
    val tokenProgram: PublicKey = Programs.TOKEN,
    val featured: Boolean = false,
) {
    val isSol: Boolean get() = mint == null
    val uiAmount: BigDecimal get() = BigDecimal(amount).movePointLeft(decimals)

    fun formatted(maxFraction: Int = 4): String = formatAmount(uiAmount, maxFraction)

    fun toRaw(ui: BigDecimal): Long = ui.movePointRight(decimals).setScale(0, RoundingMode.DOWN).toLong()
}

fun formatAmount(value: BigDecimal, maxFraction: Int = 4): String {
    val format = DecimalFormat("#,##0.${"#".repeat(maxFraction)}", DecimalFormatSymbols(Locale.US))
    format.roundingMode = RoundingMode.DOWN
    return format.format(value)
}

fun formatSol(lamports: Long, maxFraction: Int = 4): String =
    formatAmount(BigDecimal(lamports).movePointLeft(9), maxFraction)
