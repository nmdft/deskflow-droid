package com.itesaurabh.deskflowclient

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * A self-signed RSA certificate, built by hand because Android ships no public certificate
 * generator: `java.security.cert` only parses, and the bundled BouncyCastle lives in a hidden
 * `com.android.org` package.
 *
 * AndroidKeyStore does mint a certificate alongside a generated key, which is the obvious route,
 * but its keys are opaque to Conscrypt and refuse any signature outside the authorisations
 * declared at generation time, reported only as "RSA routines: internal error", with the actual
 * reason stuck on the far side of JNI. A software key has no such list to get wrong.
 *
 * This is deliberately a v1 certificate with no extensions: Deskflow's server asks for a client
 * certificate and then hands verification to a callback that returns success unconditionally, so
 * the certificate only has to exist and parse.
 */
object SelfSignedCert {

    private const val TEN_YEARS_MS = 10L * 365 * 24 * 60 * 60 * 1000
    private const val ONE_DAY_MS = 24L * 60 * 60 * 1000

    /** AlgorithmIdentifier for sha256WithRSAEncryption (1.2.840.113549.1.1.11), NULL params. */
    private val SHA256_WITH_RSA = byteArrayOf(
        0x30, 0x0D, 0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(),
        0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B, 0x05, 0x00
    )

    /** OID 2.5.4.3, commonName. */
    private val COMMON_NAME = byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03)

    fun generate(commonName: String): Pair<PrivateKey, X509Certificate> {
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        val name = Der.sequence(Der.set(Der.sequence(COMMON_NAME, Der.utf8(commonName))))
        val now = System.currentTimeMillis()
        val validity = Der.sequence(
            Der.utcTime(now - ONE_DAY_MS),
            Der.utcTime(now + TEN_YEARS_MS)
        )

        val tbsCertificate = Der.sequence(
            Der.integer(BigInteger.ONE), // serial
            SHA256_WITH_RSA,
            name,                        // issuer
            validity,
            name,                        // subject
            keys.public.encoded          // SubjectPublicKeyInfo, already DER
        )

        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(keys.private)
            update(tbsCertificate)
            sign()
        }

        val certificate = Der.sequence(tbsCertificate, SHA256_WITH_RSA, Der.bitString(signature))
        val parsed = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(certificate)) as X509Certificate

        return keys.private to parsed
    }

    private object Der {
        fun sequence(vararg parts: ByteArray) = tagged(0x30, parts)
        fun set(vararg parts: ByteArray) = tagged(0x31, parts)

        fun integer(value: BigInteger) = tagged(0x02, arrayOf(value.toByteArray()))
        fun utf8(value: String) = tagged(0x0C, arrayOf(value.toByteArray(Charsets.UTF_8)))

        /** BIT STRING carries a leading count of unused trailing bits, always zero for us. */
        fun bitString(value: ByteArray) = tagged(0x03, arrayOf(byteArrayOf(0), value))

        fun utcTime(millis: Long): ByteArray {
            val format = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
            return tagged(0x17, arrayOf(format.format(Date(millis)).toByteArray(Charsets.US_ASCII)))
        }

        private fun tagged(tag: Int, parts: Array<out ByteArray>): ByteArray {
            val body = ByteArrayOutputStream().apply { parts.forEach { write(it) } }.toByteArray()
            val out = ByteArrayOutputStream()
            out.write(tag)
            if (body.size < 0x80) {
                out.write(body.size)
            } else {
                // Long form: a count of length bytes, then the length, big-endian, no leading zero.
                val length = ArrayDeque<Int>()
                var remaining = body.size
                while (remaining > 0) {
                    length.addFirst(remaining and 0xFF)
                    remaining = remaining ushr 8
                }
                out.write(0x80 or length.size)
                length.forEach { out.write(it) }
            }
            out.write(body)
            return out.toByteArray()
        }
    }
}
