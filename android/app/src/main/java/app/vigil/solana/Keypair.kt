package app.vigil.solana

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

/** Ed25519 keypair backed by a 32-byte seed. */
class Keypair private constructor(private val seed: ByteArray) {
    private val privateKey = Ed25519PrivateKeyParameters(seed, 0)

    val publicKey: PublicKey = PublicKey(privateKey.generatePublicKey().encoded)

    fun sign(message: ByteArray): ByteArray = Ed25519Signer().run {
        init(true, privateKey)
        update(message, 0, message.size)
        generateSignature()
    }

    fun seedBytes(): ByteArray = seed.copyOf()

    companion object {
        fun generate(): Keypair = Keypair(ByteArray(32).also { SecureRandom().nextBytes(it) })
        fun fromSeed(seed: ByteArray): Keypair {
            require(seed.size == 32) { "Seed must be 32 bytes" }
            return Keypair(seed.copyOf())
        }
    }
}
