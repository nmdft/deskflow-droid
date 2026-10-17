package com.itesaurabh.deskflowclient

import android.content.Context
import android.util.Base64
import java.net.Socket
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

/**
 * The server presented a certificate we have not pinned. Carries the fingerprint so the UI can
 * show it and let the user compare it against the one Deskflow displays on the desktop.
 */
class UntrustedServer(val fingerprint: String, val pinned: String?) :
    CertificateException("server fingerprint $fingerprint does not match ${pinned ?: "any pinned certificate"}")

/**
 * Deskflow's TLS is not the usual client-verifies-a-CA arrangement:
 *
 * - The server's certificate is self-signed (CN=Deskflow), so chain validation is meaningless and
 *   is replaced by trust-on-first-use pinning of the SHA-256 fingerprint.
 * - With TLS enabled the server runs at `SecurityLevel::PeerAuth`, which sets
 *   `SSL_VERIFY_FAIL_IF_NO_PEER_CERT` and then ignores the verification result. So we must send a
 *   client certificate, but it only has to exist.
 */
object Tls {
    private const val ALIAS = "deskflow-client"
    private const val PIN_KEY = "server_fingerprint"
    private const val KEY_PRIVATE = "client_key"
    private const val KEY_CERT = "client_cert"

    @Volatile
    private var identity: Pair<PrivateKey, X509Certificate>? = null

    fun fingerprintOf(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256")
            .digest(cert.encoded)
            .joinToString(":") { "%02X".format(it) }

    fun pinnedFingerprint(context: Context): String? = prefs(context).getString(PIN_KEY, null)

    fun pin(context: Context, fingerprint: String) {
        prefs(context).edit().putString(PIN_KEY, fingerprint).apply()
    }

    fun forget(context: Context) {
        prefs(context).edit().remove(PIN_KEY).apply()
    }

    /**
     * Hand the connected TCP socket to TLS. Deskflow negotiates nothing first: the handshake runs
     * immediately on connect, before the hello packet.
     *
     * ponytail: pinned to TLS 1.2, which is Deskflow's own floor. Drop the pin if 1.3 is ever
     * wanted; the client key authorises both PKCS#1 and PSS, so it should carry over.
     */
    fun wrap(context: Context, socket: Socket, host: String, port: Int): SSLSocket {
        val ssl = SSLContext.getInstance("TLSv1.2").apply {
            init(
                arrayOf(ClientKeyManager(clientIdentity(context))),
                arrayOf(PinnedTrustManager(pinnedFingerprint(context))),
                null
            )
        }.socketFactory.createSocket(socket, host, port, true) as SSLSocket

        ssl.enabledProtocols = arrayOf("TLSv1.2")
        ssl.startHandshake()
        Logs.add("negotiated ${ssl.session.protocol} ${ssl.session.cipherSuite}")
        return ssl
    }

    private fun prefs(context: Context) = context.getSharedPreferences("deskflow", Context.MODE_PRIVATE)

    /** SHA-256 of this device's own certificate, as the desktop sees it when it asks to trust us. */
    fun localFingerprintBytes(context: Context): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(clientIdentity(context).second.encoded)

    /**
     * Persisted, not regenerated per process. The desktop pins *this* fingerprint when it approves
     * the device, so a fresh certificate on every launch would make it ask again every time.
     *
     * ponytail: stored in app-private preferences, unencrypted. The key authenticates nothing (the
     * server accepts any client certificate), so it is worth no more than the approval it carries.
     * Move it to the keystore if that ever stops being true.
     */
    private fun clientIdentity(context: Context): Pair<PrivateKey, X509Certificate> =
        identity ?: synchronized(this) {
            identity ?: load(context).also { identity = it }
        }

    private fun load(context: Context): Pair<PrivateKey, X509Certificate> {
        val prefs = prefs(context)
        val storedKey = prefs.getString(KEY_PRIVATE, null)
        val storedCert = prefs.getString(KEY_CERT, null)

        if (storedKey != null && storedCert != null) {
            runCatching {
                val key = KeyFactory.getInstance("RSA")
                    .generatePrivate(PKCS8EncodedKeySpec(Base64.decode(storedKey, Base64.NO_WRAP)))
                val cert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(Base64.decode(storedCert, Base64.NO_WRAP).inputStream()) as X509Certificate
                return key to cert
            }.onFailure { Logs.warn("stored client certificate unreadable, making a new one") }
        }

        Logs.add("generating this device's certificate")
        val fresh = SelfSignedCert.generate("Deskflow Droid")
        prefs.edit()
            .putString(KEY_PRIVATE, Base64.encodeToString(fresh.first.encoded, Base64.NO_WRAP))
            .putString(KEY_CERT, Base64.encodeToString(fresh.second.encoded, Base64.NO_WRAP))
            .apply()
        return fresh
    }

    private class ClientKeyManager(identity: Pair<PrivateKey, X509Certificate>) : X509ExtendedKeyManager() {
        private val key = identity.first
        private val chain = arrayOf(identity.second)

        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
        override fun getCertificateChain(alias: String?) = chain
        override fun getPrivateKey(alias: String?): PrivateKey = key

        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
    }

    private class PinnedTrustManager(private val expected: String?) : X509TrustManager {
        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String?) {
            val actual = fingerprintOf(chain.first())
            Logs.add("server certificate $actual")
            if (!actual.equals(expected, ignoreCase = true)) {
                throw UntrustedServer(actual, expected)
            }
            Logs.add("fingerprint matches the pinned certificate")
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String?) =
            throw CertificateException("this is a client, it authenticates nobody")

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
