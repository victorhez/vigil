package app.vigil.solana

import java.math.BigInteger
import java.security.MessageDigest

class PublicKey(bytes: ByteArray) : Comparable<PublicKey> {
    private val raw: ByteArray = bytes.copyOf()

    init {
        require(raw.size == 32) { "Public key must be 32 bytes" }
    }

    val bytes: ByteArray get() = raw.copyOf()

    fun toBase58(): String = Base58.encode(raw)

    fun short(head: Int = 4, tail: Int = 4): String = toBase58().let { "${it.take(head)}…${it.takeLast(tail)}" }

    override fun toString(): String = toBase58()
    override fun equals(other: Any?): Boolean = other is PublicKey && raw.contentEquals(other.raw)
    override fun hashCode(): Int = raw.contentHashCode()
    override fun compareTo(other: PublicKey): Int = toBase58().compareTo(other.toBase58())

    companion object {
        val DEFAULT = PublicKey(ByteArray(32))

        fun of(base58: String): PublicKey = PublicKey(Base58.decode(base58.trim()))

        fun parseOrNull(value: String): PublicKey? = runCatching {
            val decoded = Base58.decode(value.trim())
            if (decoded.size == 32) PublicKey(decoded) else null
        }.getOrNull()

        fun createProgramAddress(seeds: List<ByteArray>, programId: PublicKey): PublicKey? {
            val digest = MessageDigest.getInstance("SHA-256")
            seeds.forEach {
                require(it.size <= 32) { "Seed longer than 32 bytes" }
                digest.update(it)
            }
            digest.update(programId.raw)
            digest.update("ProgramDerivedAddress".toByteArray())
            val hash = digest.digest()
            return if (Ed25519Curve.isOnCurve(hash)) null else PublicKey(hash)
        }

        fun findProgramAddress(seeds: List<ByteArray>, programId: PublicKey): Pair<PublicKey, Int> {
            for (bump in 255 downTo 0) {
                val address = createProgramAddress(seeds + byteArrayOf(bump.toByte()), programId)
                if (address != null) return address to bump
            }
            error("Unable to find a viable program address")
        }
    }
}

/** Minimal Ed25519 point decompression, used only to decide whether a PDA candidate is off-curve. */
internal object Ed25519Curve {
    private val P = BigInteger.TWO.pow(255) - BigInteger.valueOf(19)
    private val D = BigInteger.valueOf(-121665).multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P)

    fun isOnCurve(encoded: ByteArray): Boolean {
        val le = encoded.copyOf()
        le[31] = (le[31].toInt() and 0x7f).toByte()
        val y = BigInteger(1, le.reversedArray()).mod(P)
        val y2 = y.multiply(y).mod(P)
        val u = (y2 - BigInteger.ONE).mod(P)
        val v = (D.multiply(y2) + BigInteger.ONE).mod(P)
        val x2 = u.multiply(v.modInverse(P)).mod(P)
        if (x2 == BigInteger.ZERO) return true
        // Euler's criterion: x2 is a square mod P iff x2^((P-1)/2) == 1.
        return x2.modPow((P - BigInteger.ONE).shiftRight(1), P) == BigInteger.ONE
    }
}
