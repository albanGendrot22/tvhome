package fr.alban.tvhome

import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Client ADB minimal : l'appli se connecte au mode développeur de la TV elle-même (127.0.0.1:5555),
 * exactement comme ton PC, pour lancer quelques commandes "pm" et "settings".
 * Aucune dépendance Android ici, pour pouvoir le tester sur un ordinateur.
 */
class AdbClient(private val keyDir: File, private val keyName: String = "tvhome@android-tv") {

    class AdbException(msg: String) : IOException(msg)

    private var socket: Socket? = null
    private lateinit var input: DataInputStream
    private lateinit var output: OutputStream
    private var nextLocalId = 1

    private val keys: KeyPair by lazy { loadOrCreateKeys() }

    /**
     * Ouvre la connexion. La première fois, la TV affiche "Autoriser le débogage USB ?" :
     * on attend alors jusqu'à [approvalTimeoutMs] que quelqu'un valide.
     */
    fun connect(host: String = "127.0.0.1", port: Int = 5555, approvalTimeoutMs: Int = 45_000) {
        val s = Socket()
        s.connect(InetSocketAddress(host, port), 3_000)
        s.soTimeout = 8_000
        socket = s
        input = DataInputStream(s.getInputStream().buffered())
        output = s.getOutputStream()

        send(CNXN, VERSION, MAX_DATA, "host::\u0000".toByteArray())
        var sentSignature = false
        var sentPublicKey = false
        while (true) {
            val m = read()
            when (m.command) {
                CNXN -> return
                AUTH -> {
                    if (m.arg0 != AUTH_TOKEN) throw AdbException("Réponse d'authentification inattendue")
                    when {
                        !sentSignature -> {
                            send(AUTH, AUTH_SIGNATURE, 0, sign(m.data))
                            sentSignature = true
                        }
                        !sentPublicKey -> {
                            // Clé inconnue de la TV : on l'envoie, la popup d'autorisation s'affiche
                            send(AUTH, AUTH_RSAPUBLICKEY, 0, publicKeyPayload())
                            sentPublicKey = true
                            s.soTimeout = approvalTimeoutMs
                        }
                        else -> throw AdbException("Autorisation refusée par la TV")
                    }
                }
                else -> { /* message parasite avant la connexion : on l'ignore */ }
            }
        }
    }

    /** Lance une commande shell et renvoie ce qu'elle affiche. */
    fun shell(command: String, timeoutMs: Int = 15_000): String {
        val s = socket ?: throw AdbException("Pas connecté")
        s.soTimeout = timeoutMs
        val localId = nextLocalId++
        send(OPEN, localId, 0, "shell:$command\u0000".toByteArray())
        val out = StringBuilder()
        var remoteId = 0
        while (true) {
            val m = read()
            if (m.arg1 != localId && m.command != CNXN) continue
            when (m.command) {
                OKAY -> remoteId = m.arg0
                WRTE -> {
                    out.append(String(m.data))
                    send(OKAY, localId, m.arg0, ByteArray(0))
                }
                CLSE -> {
                    if (remoteId != 0) send(CLSE, localId, remoteId, ByteArray(0))
                    return out.toString()
                }
            }
        }
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    // --- Messages ---

    private class Message(val command: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    private fun send(command: Int, arg0: Int, arg1: Int, data: ByteArray) {
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command).putInt(arg0).putInt(arg1)
            .putInt(data.size).putInt(checksum(data)).putInt(command.inv())
            .array()
        output.write(header)
        if (data.isNotEmpty()) output.write(data)
        output.flush()
    }

    private fun read(): Message {
        val header = ByteArray(24)
        input.readFully(header)
        val b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = b.int
        val arg0 = b.int
        val arg1 = b.int
        val length = b.int
        b.int // somme de contrôle, que les versions récentes d'adbd ne remplissent plus
        val magic = b.int
        if (magic != command.inv()) throw AdbException("Message ADB corrompu")
        if (length < 0 || length > 1 shl 20) throw AdbException("Message ADB trop long")
        val data = ByteArray(length)
        input.readFully(data)
        return Message(command, arg0, arg1, data)
    }

    private fun checksum(data: ByteArray): Int {
        var sum = 0
        for (x in data) sum += x.toInt() and 0xFF
        return sum
    }

    // --- Clés RSA ---

    private fun loadOrCreateKeys(): KeyPair {
        val priv = File(keyDir, "adbkey")
        val pub = File(keyDir, "adbkey.pub")
        if (priv.exists() && pub.exists()) {
            runCatching {
                val kf = KeyFactory.getInstance("RSA")
                return KeyPair(
                    kf.generatePublic(X509EncodedKeySpec(pub.readBytes())),
                    kf.generatePrivate(PKCS8EncodedKeySpec(priv.readBytes())),
                )
            }
        }
        keyDir.mkdirs()
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        priv.writeBytes(pair.private.encoded)
        pub.writeBytes(pair.public.encoded)
        return pair
    }

    /**
     * ADB signe le jeton comme si c'était déjà une empreinte SHA-1 (PKCS#1 v1.5, type 1).
     * Le bourrage et le calcul RSA sont faits à la main pour ne dépendre d'aucun moteur crypto.
     */
    private fun sign(token: ByteArray): ByteArray {
        val priv = keys.private as RSAPrivateKey
        val size = (priv.modulus.bitLength() + 7) / 8
        val payload = SHA1_DIGEST_INFO + token
        val em = ByteArray(size) { 0xFF.toByte() }
        em[0] = 0x00
        em[1] = 0x01
        em[size - payload.size - 1] = 0x00
        payload.copyInto(em, size - payload.size)
        val sig = BigInteger(1, em).modPow(priv.privateExponent, priv.modulus).toByteArray()
        // toByteArray() peut ajouter un zéro de signe ou en retirer : on recale sur la taille exacte
        return when {
            sig.size == size -> sig
            sig.size > size -> sig.copyOfRange(sig.size - size, sig.size)
            else -> ByteArray(size - sig.size) + sig
        }
    }

    /** Clé publique au format d'Android (structure "mincrypt"), en base64, suivie d'un nom. */
    private fun publicKeyPayload(): ByteArray {
        val key = keys.public as RSAPublicKey
        val n = key.modulus
        val words = 2048 / 32
        val r32 = BigInteger.ONE.shiftLeft(32)
        val n0inv = r32.subtract(n.mod(r32).modInverse(r32))
        val rr = BigInteger.ONE.shiftLeft(2 * 2048).mod(n)
        val buf = ByteBuffer.allocate(4 + 4 + words * 4 + words * 4 + 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(words)
        buf.putInt(n0inv.toInt())
        putWords(buf, n, words)
        putWords(buf, rr, words)
        buf.putInt(key.publicExponent.toInt())
        val b64 = Base64.getEncoder().encodeToString(buf.array())
        return "$b64 $keyName\u0000".toByteArray()
    }

    private fun putWords(buf: ByteBuffer, value: BigInteger, words: Int) {
        val mask = BigInteger.valueOf(0xFFFFFFFFL)
        var v = value
        repeat(words) {
            buf.putInt(v.and(mask).toLong().toInt())
            v = v.shiftRight(32)
        }
    }

    companion object {
        private const val CNXN = 0x4e584e43
        private const val AUTH = 0x48545541
        private const val OPEN = 0x4e45504f
        private const val OKAY = 0x59414b4f
        private const val CLSE = 0x45534c43
        private const val WRTE = 0x45545257

        private const val VERSION = 0x01000000
        private const val MAX_DATA = 4096

        private const val AUTH_TOKEN = 1
        private const val AUTH_SIGNATURE = 2
        private const val AUTH_RSAPUBLICKEY = 3

        private val SHA1_DIGEST_INFO = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14,
        )
    }
}
