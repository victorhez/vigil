package app.vigil.solana

import java.math.BigInteger

object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val INDEXES = IntArray(128) { -1 }.also { idx ->
        ALPHABET.forEachIndexed { i, c -> idx[c.code] = i }
    }
    private val BASE = BigInteger.valueOf(58)

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""
        val zeros = input.takeWhile { it == 0.toByte() }.size
        var value = BigInteger(1, input)
        val sb = StringBuilder()
        while (value > BigInteger.ZERO) {
            val (q, r) = value.divideAndRemainder(BASE)
            sb.append(ALPHABET[r.toInt()])
            value = q
        }
        repeat(zeros) { sb.append('1') }
        return sb.reverse().toString()
    }

    fun decode(input: String): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        var value = BigInteger.ZERO
        for (c in input) {
            val digit = if (c.code < 128) INDEXES[c.code] else -1
            require(digit >= 0) { "Invalid base58 character '$c'" }
            value = value.multiply(BASE).add(BigInteger.valueOf(digit.toLong()))
        }
        val zeros = input.takeWhile { it == '1' }.length
        val bytes = value.toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
        val body = if (value == BigInteger.ZERO) ByteArray(0) else bytes
        return ByteArray(zeros) + body
    }
}
