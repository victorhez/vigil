package app.vigil.solana

import java.io.ByteArrayOutputStream

data class AccountMeta(val pubkey: PublicKey, val isSigner: Boolean, val isWritable: Boolean) {
    companion object {
        fun signer(key: PublicKey, writable: Boolean = false) = AccountMeta(key, true, writable)
        fun writable(key: PublicKey) = AccountMeta(key, false, true)
        fun readonly(key: PublicKey) = AccountMeta(key, false, false)
    }
}

class Instruction(val programId: PublicKey, val accounts: List<AccountMeta>, val data: ByteArray)

/**
 * Legacy Solana transaction. Compiles instructions into a message with deduplicated,
 * correctly ordered account keys, and holds one signature slot per required signer.
 */
class Transaction(
    private val feePayer: PublicKey,
    private val recentBlockhash: String,
    private val instructions: List<Instruction>,
) {
    private val accountKeys: List<PublicKey>
    private val numSigners: Int
    private val numReadonlySigned: Int
    private val numReadonlyUnsigned: Int
    val message: ByteArray
    private val signatures: Array<ByteArray>

    init {
        val metas = LinkedHashMap<PublicKey, AccountMeta>()
        metas[feePayer] = AccountMeta(feePayer, isSigner = true, isWritable = true)
        instructions.forEach { ix ->
            ix.accounts.forEach { meta ->
                val prev = metas[meta.pubkey]
                metas[meta.pubkey] = if (prev == null) meta else AccountMeta(
                    meta.pubkey,
                    prev.isSigner || meta.isSigner,
                    prev.isWritable || meta.isWritable,
                )
            }
            if (!metas.containsKey(ix.programId)) metas[ix.programId] = AccountMeta.readonly(ix.programId)
        }

        val payer = metas.remove(feePayer)!!
        val rest = metas.values.toList()
        val ordered = listOf(payer) +
            rest.filter { it.isSigner && it.isWritable } +
            rest.filter { it.isSigner && !it.isWritable } +
            rest.filter { !it.isSigner && it.isWritable } +
            rest.filter { !it.isSigner && !it.isWritable }

        accountKeys = ordered.map { it.pubkey }
        numSigners = ordered.count { it.isSigner }
        numReadonlySigned = ordered.count { it.isSigner && !it.isWritable }
        numReadonlyUnsigned = ordered.count { !it.isSigner && !it.isWritable }
        message = compileMessage()
        signatures = Array(numSigners) { ByteArray(64) }
    }

    val signerKeys: List<PublicKey> get() = accountKeys.take(numSigners)

    private fun compileMessage(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(numSigners)
        out.write(numReadonlySigned)
        out.write(numReadonlyUnsigned)
        out.writeShortVec(accountKeys.size)
        accountKeys.forEach { out.write(it.bytes) }
        out.write(Base58.decode(recentBlockhash))
        out.writeShortVec(instructions.size)
        instructions.forEach { ix ->
            out.write(accountKeys.indexOf(ix.programId))
            out.writeShortVec(ix.accounts.size)
            ix.accounts.forEach { out.write(accountKeys.indexOf(it.pubkey)) }
            out.writeShortVec(ix.data.size)
            out.write(ix.data)
        }
        return out.toByteArray()
    }

    fun sign(vararg keypairs: Keypair): Transaction {
        keypairs.forEach { kp ->
            val index = signerKeys.indexOf(kp.publicKey)
            require(index >= 0) { "${kp.publicKey} is not a signer of this transaction" }
            signatures[index] = kp.sign(message)
        }
        return this
    }

    /** First signature, which is the transaction id once the fee payer has signed. */
    val signature: String get() = Base58.encode(signatures[0])

    fun serialize(): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeShortVec(signatures.size)
        signatures.forEach { out.write(it) }
        out.write(message)
        return out.toByteArray()
    }
}

internal fun ByteArrayOutputStream.writeShortVec(value: Int) {
    var rem = value
    while (true) {
        var elem = rem and 0x7f
        rem = rem shr 7
        if (rem == 0) {
            write(elem)
            return
        }
        elem = elem or 0x80
        write(elem)
    }
}
