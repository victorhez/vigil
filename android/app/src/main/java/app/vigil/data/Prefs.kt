package app.vigil.data

import android.content.Context
import androidx.core.content.edit
import app.vigil.solana.PublicKey
import app.vigil.solana.VaultAccount
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Snapshot of the on-chain vault, cached so the widget, tile and reminders work without a network round trip. */
@Serializable
data class VaultSnapshot(
    val vault: String,
    val lastPulse: Long,
    val interval: Long,
    val grace: Long,
    val streak: Int,
    val releasedAt: Long,
    val syncedAt: Long,
) {
    val dueAt: Long get() = lastPulse + interval
    val deadline: Long get() = lastPulse + interval + grace

    companion object {
        fun of(vault: VaultAccount) = VaultSnapshot(
            vault.address.toBase58(), vault.lastPulse, vault.interval, vault.grace,
            vault.streak, vault.releasedAt, System.currentTimeMillis() / 1000,
        )
    }
}

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("vigil", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(value) = prefs.edit { putBoolean("onboarded", value) }

    var wallet: PublicKey?
        get() = prefs.getString("wallet", null)?.let(PublicKey::parseOrNull)
        set(value) = prefs.edit { putString("wallet", value?.toBase58()) }

    var walletLabel: String?
        get() = prefs.getString("wallet_label", null)
        set(value) = prefs.edit { putString("wallet_label", value) }

    var authToken: String?
        get() = prefs.getString("auth_token", null)
        set(value) = prefs.edit { putString("auth_token", value) }

    var remindersEnabled: Boolean
        get() = prefs.getBoolean("reminders", true)
        set(value) = prefs.edit { putBoolean("reminders", value) }

    var snapshot: VaultSnapshot?
        get() = prefs.getString("snapshot", null)?.let { runCatching { json.decodeFromString<VaultSnapshot>(it) }.getOrNull() }
        set(value) = prefs.edit { putString("snapshot", value?.let { json.encodeToString(VaultSnapshot.serializer(), it) }) }

    /** Last reminder stage notified for a given deadline, so each stage fires once. */
    fun reminderStage(deadline: Long): Int = if (prefs.getLong("reminder_deadline", 0) == deadline) prefs.getInt("reminder_stage", 0) else 0

    fun setReminderStage(deadline: Long, stage: Int) = prefs.edit {
        putLong("reminder_deadline", deadline)
        putInt("reminder_stage", stage)
    }

    /** Heir nicknames stay on the device. Only wallet addresses and shares go on-chain. */
    fun heirLabel(wallet: PublicKey): String? = prefs.getString("heir_${wallet.toBase58()}", null)

    fun setHeirLabel(wallet: PublicKey, label: String) = prefs.edit {
        putString("heir_${wallet.toBase58()}", label.trim().ifEmpty { null })
    }

    fun clearSession() = prefs.edit {
        remove("wallet"); remove("wallet_label"); remove("auth_token"); remove("snapshot")
    }
}
