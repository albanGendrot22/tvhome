package fr.alban.tvhome

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume

/** Une pièce Hue, avec son groupe de lumières (pour tout allumer ou régler d'un coup). */
data class HueRoom(
    val id: String,
    val name: String,
    val groupedLight: String?,
    val on: Boolean,
    val brightness: Float,
    val lights: List<String>,
)

data class HueScene(val id: String, val name: String, val roomId: String)

/** L'état d'une lumière, pour pouvoir la remettre exactement comme avant. */
data class HueLightState(val id: String, val on: Boolean, val brightness: Float?, val mirek: Int?, val x: Double?, val y: Double?) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("on", on)
        .apply { brightness?.let { put("bri", it.toDouble()) } }
        .apply { mirek?.let { put("mirek", it) } }
        .apply { if (x != null && y != null) put("x", x).put("y", y) }

    companion object {
        fun fromJson(o: JSONObject) = HueLightState(
            o.getString("id"), o.getBoolean("on"),
            if (o.has("bri")) o.getDouble("bri").toFloat() else null,
            if (o.has("mirek")) o.getInt("mirek") else null,
            if (o.has("x")) o.getDouble("x") else null,
            if (o.has("y")) o.getDouble("y") else null,
        )
    }
}

/**
 * Le pont Philips Hue, en local uniquement (API v2 "CLIP"). On lit et on pilote les lumières,
 * on rappelle les scènes existantes ; on ne crée ni ne modifie jamais rien dans le pont.
 *
 * Le pont a un certificat auto-signé : on retient son empreinte à l'appairage, puis on refuse
 * tout autre certificat (un autre appareil qui se ferait passer pour le pont n'aurait pas la clé).
 */
object HueBridge {
    private const val TAG = "TvHomeHue"
    private const val TIMEOUT_MS = 3_000

    /** Cherche le pont sur le réseau local (mDNS), sinon demande au service de découverte de Philips. */
    suspend fun discover(ctx: Context): String? =
        withTimeoutOrNull(6_000) { discoverMdns(ctx) } ?: discoverCloud()

    private suspend fun discoverMdns(ctx: Context): String? = suspendCancellableCoroutine { cont ->
        val nsd = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val ip = resolved.host?.hostAddress
                        if (ip != null && cont.isActive) cont.resume(ip)
                    }
                    override fun onResolveFailed(info: NsdServiceInfo, error: Int) {}
                })
            }
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onStartDiscoveryFailed(type: String, error: Int) { if (cont.isActive) cont.resume(null) }
            override fun onStopDiscoveryFailed(type: String, error: Int) {}
        }
        runCatching { nsd.discoverServices("_hue._tcp", NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { if (cont.isActive) cont.resume(null) }
        cont.invokeOnCancellation { runCatching { nsd.stopServiceDiscovery(listener) } }
    }

    private suspend fun discoverCloud(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("https://discovery.meethue.com/").openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS; conn.readTimeout = TIMEOUT_MS
            val body = try { conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
            JSONArray(body).optJSONObject(0)?.optString("internalipaddress")?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /**
     * Demande une clé au pont. Ne réussit que dans les 30 s qui suivent l'appui sur son bouton :
     * renvoie null tant que le bouton n'a pas été pressé. Retient l'empreinte du certificat au passage.
     */
    suspend fun requestKey(ip: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        runCatching {
            var seen: String? = null
            val conn = open("https://$ip/api", pin = null) { seen = it }
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use {
                it.write(JSONObject().put("devicetype", "tvhome#edenwood").put("generateclientkey", true).toString().toByteArray())
            }
            val reply = JSONArray(conn.inputStream.bufferedReader().use { it.readText() }).getJSONObject(0)
            val key = reply.optJSONObject("success")?.optString("username")?.takeIf { it.isNotBlank() }
            if (key != null && seen != null) key to seen!! else null
        }.onFailure { Log.w(TAG, "Appairage : pas de réponse", it) }.getOrNull()
    }

    // --- Lecture ---

    suspend fun reachable(c: HueConfig): Boolean = withContext(Dispatchers.IO) {
        runCatching { get(c, "/clip/v2/resource/bridge"); true }.getOrDefault(false)
    }

    suspend fun rooms(c: HueConfig): List<HueRoom>? = withContext(Dispatchers.IO) {
        runCatching {
            val rooms = get(c, "/clip/v2/resource/room")
            val groups = get(c, "/clip/v2/resource/grouped_light").associateBy { it.getString("id") }
            val lights = get(c, "/clip/v2/resource/light")
            rooms.map { r ->
                val devices = r.getJSONArray("children").objects().filter { it.optString("rtype") == "device" }
                    .map { it.getString("rid") }.toSet()
                val group = r.optJSONArray("services")?.objects()
                    ?.firstOrNull { it.optString("rtype") == "grouped_light" }?.getString("rid")
                val g = group?.let { groups[it] }
                HueRoom(
                    id = r.getString("id"),
                    name = r.getJSONObject("metadata").getString("name"),
                    groupedLight = group,
                    on = g?.optJSONObject("on")?.optBoolean("on") ?: false,
                    brightness = g?.optJSONObject("dimming")?.optDouble("brightness")?.toFloat() ?: 0f,
                    lights = lights.filter { it.optJSONObject("owner")?.optString("rid") in devices }.map { it.getString("id") },
                )
            }.sortedBy { it.name.lowercase() }
        }.onFailure { Log.w(TAG, "Lecture des pièces impossible", it) }.getOrNull()
    }

    suspend fun scenes(c: HueConfig): List<HueScene>? = withContext(Dispatchers.IO) {
        runCatching {
            get(c, "/clip/v2/resource/scene").map {
                HueScene(it.getString("id"), it.getJSONObject("metadata").getString("name"), it.getJSONObject("group").getString("rid"))
            }.sortedBy { it.name.lowercase() }
        }.getOrNull()
    }

    suspend fun lightStates(c: HueConfig, ids: List<String>): List<HueLightState>? = withContext(Dispatchers.IO) {
        runCatching {
            get(c, "/clip/v2/resource/light").filter { it.getString("id") in ids }.map { l ->
                val ct = l.optJSONObject("color_temperature")
                val xy = l.optJSONObject("color")?.optJSONObject("xy")
                val mirek = ct?.takeIf { it.optBoolean("mirek_valid") }?.optInt("mirek")
                HueLightState(
                    id = l.getString("id"),
                    on = l.getJSONObject("on").getBoolean("on"),
                    brightness = l.optJSONObject("dimming")?.optDouble("brightness")?.toFloat(),
                    mirek = mirek,
                    x = if (mirek == null) xy?.optDouble("x") else null,
                    y = if (mirek == null) xy?.optDouble("y") else null,
                )
            }
        }.onFailure { Log.w(TAG, "Lecture des lumières impossible", it) }.getOrNull()
    }

    /** Lumières qui savent changer de blanc (chaud / froid). */
    suspend fun warmCapable(c: HueConfig, ids: List<String>): Set<String> = withContext(Dispatchers.IO) {
        runCatching {
            get(c, "/clip/v2/resource/light").filter { it.getString("id") in ids && it.has("color_temperature") }
                .map { it.getString("id") }.toSet()
        }.getOrDefault(emptySet())
    }

    // --- Pilotage ---

    suspend fun setGroup(c: HueConfig, groupId: String, on: Boolean? = null, brightness: Float? = null) =
        put(c, "/clip/v2/resource/grouped_light/$groupId", JSONObject().apply {
            on?.let { put("on", JSONObject().put("on", it)) }
            brightness?.let { put("dimming", JSONObject().put("brightness", it.toDouble())) }
        })

    suspend fun setLight(c: HueConfig, s: HueLightState, durationMs: Int = 800) =
        put(c, "/clip/v2/resource/light/${s.id}", JSONObject().apply {
            put("on", JSONObject().put("on", s.on))
            if (s.on) {
                s.brightness?.let { put("dimming", JSONObject().put("brightness", it.toDouble())) }
                s.mirek?.let { put("color_temperature", JSONObject().put("mirek", it)) }
                if (s.x != null && s.y != null) put("color", JSONObject().put("xy", JSONObject().put("x", s.x).put("y", s.y)))
            }
            put("dynamics", JSONObject().put("duration", durationMs))
        })

    suspend fun recallScene(c: HueConfig, sceneId: String) =
        put(c, "/clip/v2/resource/scene/$sceneId", JSONObject().put("recall", JSONObject().put("action", "active")))

    // --- HTTP ---

    private fun get(c: HueConfig, path: String): List<JSONObject> {
        val conn = open("https://${c.ip}$path", c.pin)
        conn.setRequestProperty("hue-application-key", c.key)
        try {
            if (conn.responseCode != 200) throw java.io.IOException("Hue HTTP ${conn.responseCode}")
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getJSONArray("data").objects()
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun put(c: HueConfig, path: String, body: JSONObject): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val conn = open("https://${c.ip}$path", c.pin)
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.setRequestProperty("hue-application-key", c.key)
            conn.setRequestProperty("Content-Type", "application/json")
            try {
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                conn.responseCode == 200
            } finally {
                conn.disconnect()
            }
        }.onFailure { Log.w(TAG, "Commande refusée : $path", it) }.getOrDefault(false)
    }

    /**
     * Connexion HTTPS au pont. Avec [pin] : n'accepte que le certificat retenu à l'appairage.
     * Sans [pin] (appairage seulement) : accepte le certificat et le transmet à [onCert].
     */
    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private fun open(url: String, pin: String?, onCert: (String) -> Unit = {}): HttpsURLConnection {
        val trust = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                val fp = fingerprint(chain[0])
                if (pin != null && fp != pin) throw java.security.cert.CertificateException("Ce n'est pas le pont appairé")
                onCert(fp)
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        return (URL(url).openConnection() as HttpsURLConnection).apply {
            sslSocketFactory = ssl.socketFactory as SSLSocketFactory
            // Le certificat porte l'identifiant du pont, pas son adresse IP : c'est l'empreinte qui fait foi
            hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
    }

    private fun fingerprint(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}

/** Ce qu'il faut pour parler au pont : son adresse, la clé obtenue à l'appairage, l'empreinte de son certificat. */
data class HueConfig(val ip: String, val key: String, val pin: String)
