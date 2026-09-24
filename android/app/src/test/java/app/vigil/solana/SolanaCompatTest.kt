package app.vigil.solana

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Byte-for-byte compatibility with @solana/web3.js and @solana/spl-token.
 * Reference values were produced with web3.js 1.98 from the same inputs.
 */
class SolanaCompatTest {
    private val keypair = Keypair.fromSeed(ByteArray(32) { 7 })
    private val programId = PublicKey.of("VigddEZM9A4TuLmKFkY5qwVA5eCDDniEPJ4gmGQM512")

    @Test
    fun `derives the same public key from a seed`() {
        assertEquals("GmaDrppBC7P5ARKV8g3djiwP89vz1jLK23V2GBjuAEGB", keypair.publicKey.toBase58())
    }

    @Test
    fun `base58 round-trips, including leading zeros`() {
        val bytes = byteArrayOf(0, 0, 1, 2, 3, -1)
        assertArrayEquals(bytes, Base58.decode(Base58.encode(bytes)))
        assertEquals("11111111111111111111111111111111", PublicKey.DEFAULT.toBase58())
    }

    @Test
    fun `finds the same vault PDA and bump`() {
        val (vault, bump) = PublicKey.findProgramAddress(listOf("vault".toByteArray(), keypair.publicKey.bytes), programId)
        assertEquals("J3ZeG72VA3aNQKLRKMwM22snTCgXKnTE3gLzzAVSpba3", vault.toBase58())
        assertEquals(253, bump)
    }

    @Test
    fun `finds the same associated token address`() {
        val skr = PublicKey.of("SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3")
        assertEquals("B6n6eUcbQav2hXthhUBxdaf8DzyR29477JFXjCiMWkur", TokenProgram.associatedTokenAddress(keypair.publicKey, skr).toBase58())
    }

    @Test
    fun `curve check rejects PDAs and accepts real keys`() {
        assertTrue(Ed25519Curve.isOnCurve(keypair.publicKey.bytes))
        assertFalse(Ed25519Curve.isOnCurve(PublicKey.of("J3ZeG72VA3aNQKLRKMwM22snTCgXKnTE3gLzzAVSpba3").bytes))
    }

    @Test
    fun `serializes and signs a transfer identically`() {
        val to = PublicKey.of("GT3iuxs6vmnCGtTiK44Af1sRTFrHHE2uSzTGvTGB9mQo")
        val tx = Transaction(
            keypair.publicKey,
            "EETubP5AKHgjPAhzPAFcb8BAY1hMH639CWCFTqi3hq1k",
            listOf(SystemProgram.transfer(keypair.publicKey, to, 12_345)),
        ).sign(keypair)
        val expected = "ATj4IIxP44M1OA2JdvE7/ycxulpp0yryRTYofQLKeVwLZ9KFLZIkUi0nl2+9ehbrOj901cHoF6pjF0MS5b3rPQcBAAED6kpsY+KcUgq+9VB7Ey7F+ZVHdq6+vnuSQh7qaRRG0izli3vCH6dK3b5SgwcnGNCMWaGg1H5aURgbJyXFFB6N1AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAxJrndgN4IFTxep3s6kO0ROug7bEsbx0xxuDkqEvwUusBAgIAAQwCAAAAOTAAAAAAAAA="
        assertEquals(expected, Base64.getEncoder().encodeToString(tx.serialize()))
    }

    @Test
    fun `anchor discriminator matches`() {
        assertEquals("c0e060bfbeb13f22", anchorDiscriminator("global", "pulse").joinToString("") { "%02x".format(it) })
    }

    @Test
    fun `decodes a vault account laid out like the program`() {
        val owner = keypair.publicKey
        val heir = PublicKey.of("GT3iuxs6vmnCGtTiK44Af1sRTFrHHE2uSzTGvTGB9mQo")
        val data = BorshWriter()
            .bytes(anchorDiscriminator("account", "Vault"))
            .pubkey(owner).pubkey(owner)
            .i64(86_400).i64(3_600).i64(1_000).i64(2_000)
            .u32(4).u32(9).u64(12).i64(0)
            .u8(1)
            .apply { pubkey(heir); u16(10_000); repeat(4) { pubkey(PublicKey.DEFAULT); u16(0) } }
            .u8(254)
            .toByteArray()
        assertEquals(VigilProgram.ACCOUNT_SIZE, data.size)
        val vault = VigilProgram.decode(programId, AccountData(5_000_000, programId, data))!!
        assertEquals(86_400L, vault.interval)
        assertEquals(2_000L + 86_400 + 3_600, vault.deadline)
        assertEquals(listOf(Heir(heir, 10_000)), vault.heirs)
        assertEquals(VaultPhase.Overdue, vault.phase(2_000 + 86_400 + 1))
        assertEquals(VaultPhase.Expired, vault.phase(vault.deadline + 1))
    }
}
