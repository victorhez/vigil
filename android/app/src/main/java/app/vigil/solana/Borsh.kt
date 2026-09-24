package app.vigil.solana

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BorshWriter {
    private val out = ByteArrayOutputStream()

    fun bytes(value: ByteArray) = apply { out.write(value) }
    fun u8(value: Int) = apply { out.write(value and 0xff) }
    fun u16(value: Int) = apply { out.write(le(2) { putShort(value.toShort()) }) }
    fun u32(value: Int) = apply { out.write(le(4) { putInt(value) }) }
    fun u64(value: Long) = apply { out.write(le(8) { putLong(value) }) }
    fun i64(value: Long) = u64(value)
    fun pubkey(value: PublicKey) = bytes(value.bytes)

    fun toByteArray(): ByteArray = out.toByteArray()

    private inline fun le(size: Int, block: ByteBuffer.() -> Unit): ByteArray =
        ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply(block).array()
}

class BorshReader(private val data: ByteArray, start: Int = 0) {
    private val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).apply { position(start) }

    fun u8(): Int = buf.get().toInt() and 0xff
    fun u16(): Int = buf.short.toInt() and 0xffff
    fun u32(): Long = buf.int.toLong() and 0xffffffffL
    fun u64(): Long = buf.long
    fun i64(): Long = buf.long
    fun pubkey(): PublicKey = PublicKey(ByteArray(32).also { buf.get(it) })
    fun skip(n: Int) { buf.position(buf.position() + n) }
}
