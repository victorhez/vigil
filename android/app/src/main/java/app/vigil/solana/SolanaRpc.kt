package app.vigil.solana

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class RpcException(val code: Int, message: String, val logs: List<String> = emptyList()) : Exception(message)

data class AccountData(val lamports: Long, val owner: PublicKey, val data: ByteArray)

data class ParsedTokenAccount(
    val address: PublicKey,
    val mint: PublicKey,
    val amount: Long,
    val decimals: Int,
    val tokenProgram: PublicKey,
)

data class SignatureInfo(val signature: String, val blockTime: Long?, val failed: Boolean)

class SolanaRpc(private val endpoint: String) {
    private val json = Json { ignoreUnknownKeys = true }
    private val ids = AtomicLong(1)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private suspend fun call(method: String, params: JsonArray = JsonArray(emptyList())): JsonElement =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", ids.getAndIncrement())
                put("method", method)
                put("params", params)
            }.toString().toRequestBody(JSON_TYPE)
            val request = Request.Builder().url(endpoint).post(body).build()
            http.newCall(request).execute().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) throw RpcException(response.code, "RPC HTTP ${response.code}")
                val root = json.parseToJsonElement(text).jsonObject
                root["error"]?.takeIf { it !is JsonNull }?.jsonObject?.let { err ->
                    val logs = err["data"]?.let { d ->
                        (d as? JsonObject)?.get("logs")?.let { l -> (l as? JsonArray)?.map { it.jsonPrimitive.content } }
                    } ?: emptyList()
                    throw RpcException(
                        err["code"]?.jsonPrimitive?.int ?: -1,
                        err["message"]?.jsonPrimitive?.contentOrNull ?: "RPC error",
                        logs,
                    )
                }
                root["result"] ?: JsonNull
            }
        }

    private fun commitment() = buildJsonObject { put("commitment", "confirmed") }

    suspend fun getLatestBlockhash(): String =
        call("getLatestBlockhash", buildJsonArray { add(commitment()) })
            .jsonObject["value"]!!.jsonObject["blockhash"]!!.jsonPrimitive.content

    suspend fun getBalance(address: PublicKey): Long =
        call("getBalance", buildJsonArray { add(JsonPrimitive(address.toBase58())); add(commitment()) })
            .jsonObject["value"]!!.jsonPrimitive.long

    suspend fun getAccountInfo(address: PublicKey): AccountData? {
        val params = buildJsonArray {
            add(JsonPrimitive(address.toBase58()))
            add(buildJsonObject { put("encoding", "base64"); put("commitment", "confirmed") })
        }
        val value = call("getAccountInfo", params).jsonObject["value"]
        if (value == null || value is JsonNull) return null
        return decodeAccount(value.jsonObject)
    }

    suspend fun getMinimumBalanceForRentExemption(size: Int): Long =
        call("getMinimumBalanceForRentExemption", buildJsonArray { add(JsonPrimitive(size)) }).jsonPrimitive.long

    /** Program accounts of an exact size whose bytes at `offset` equal `bytes`. */
    suspend fun getProgramAccounts(
        programId: PublicKey,
        dataSize: Int,
        memcmp: List<Pair<Int, ByteArray>>,
    ): List<Pair<PublicKey, AccountData>> {
        val params = buildJsonArray {
            add(JsonPrimitive(programId.toBase58()))
            add(buildJsonObject {
                put("encoding", "base64")
                put("commitment", "confirmed")
                put("filters", buildJsonArray {
                    add(buildJsonObject { put("dataSize", dataSize) })
                    memcmp.forEach { (offset, bytes) ->
                        add(buildJsonObject {
                            put("memcmp", buildJsonObject {
                                put("offset", offset)
                                put("bytes", Base58.encode(bytes))
                            })
                        })
                    }
                })
            })
        }
        return call("getProgramAccounts", params).jsonArray.map { entry ->
            val obj = entry.jsonObject
            PublicKey.of(obj["pubkey"]!!.jsonPrimitive.content) to decodeAccount(obj["account"]!!.jsonObject)
        }
    }

    suspend fun getTokenAccountsByOwner(owner: PublicKey, tokenProgram: PublicKey): List<ParsedTokenAccount> {
        val params = buildJsonArray {
            add(JsonPrimitive(owner.toBase58()))
            add(buildJsonObject { put("programId", tokenProgram.toBase58()) })
            add(buildJsonObject { put("encoding", "jsonParsed"); put("commitment", "confirmed") })
        }
        return call("getTokenAccountsByOwner", params).jsonObject["value"]!!.jsonArray.mapNotNull { entry ->
            val obj = entry.jsonObject
            val info = obj["account"]?.jsonObject?.get("data")?.jsonObject?.get("parsed")?.jsonObject
                ?.get("info")?.jsonObject ?: return@mapNotNull null
            val tokenAmount = info["tokenAmount"]!!.jsonObject
            ParsedTokenAccount(
                address = PublicKey.of(obj["pubkey"]!!.jsonPrimitive.content),
                mint = PublicKey.of(info["mint"]!!.jsonPrimitive.content),
                amount = tokenAmount["amount"]!!.jsonPrimitive.content.toLong(),
                decimals = tokenAmount["decimals"]!!.jsonPrimitive.int,
                tokenProgram = tokenProgram,
            )
        }
    }

    suspend fun getSignaturesForAddress(address: PublicKey, limit: Int = 25): List<SignatureInfo> {
        val params = buildJsonArray {
            add(JsonPrimitive(address.toBase58()))
            add(buildJsonObject { put("limit", limit); put("commitment", "confirmed") })
        }
        return call("getSignaturesForAddress", params).jsonArray.map { entry ->
            val obj = entry.jsonObject
            SignatureInfo(
                signature = obj["signature"]!!.jsonPrimitive.content,
                blockTime = obj["blockTime"]?.jsonPrimitive?.longOrNull,
                failed = obj["err"].let { it != null && it !is JsonNull },
            )
        }
    }

    suspend fun sendTransaction(tx: ByteArray): String {
        val params = buildJsonArray {
            add(JsonPrimitive(Base64.encodeToString(tx, Base64.NO_WRAP)))
            add(buildJsonObject {
                put("encoding", "base64")
                put("preflightCommitment", "confirmed")
            })
        }
        return call("sendTransaction", params).jsonPrimitive.content
    }

    suspend fun requestAirdrop(address: PublicKey, lamports: Long): String =
        call("requestAirdrop", buildJsonArray { add(JsonPrimitive(address.toBase58())); add(JsonPrimitive(lamports)) })
            .jsonPrimitive.content

    /** Polls until the signature reaches `confirmed`, fails, or the timeout elapses. */
    suspend fun confirm(signature: String, timeoutMs: Long = 45_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val params = buildJsonArray {
                add(buildJsonArray { add(JsonPrimitive(signature)) })
                add(buildJsonObject { put("searchTransactionHistory", true) })
            }
            val status = call("getSignatureStatuses", params).jsonObject["value"]!!.jsonArray.firstOrNull()
            if (status != null && status !is JsonNull) {
                val obj = status.jsonObject
                val err = obj["err"]
                if (err != null && err !is JsonNull) throw RpcException(-1, "Transaction failed: $err")
                val level = obj["confirmationStatus"]?.jsonPrimitive?.contentOrNull
                if (level == "confirmed" || level == "finalized") return true
            }
            delay(700)
        }
        return false
    }

    private fun decodeAccount(obj: JsonObject): AccountData {
        val data = obj["data"]!!.jsonArray[0].jsonPrimitive.content
        return AccountData(
            lamports = obj["lamports"]!!.jsonPrimitive.long,
            owner = PublicKey.of(obj["owner"]!!.jsonPrimitive.content),
            data = Base64.decode(data, Base64.DEFAULT),
        )
    }

    companion object {
        private val JSON_TYPE = "application/json".toMediaType()
    }
}
