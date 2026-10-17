package com.itesaurabh.deskflowclient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.io.ByteArrayInputStream

class SelfSignedCertTest {

    /**
     * generate() already round-trips its DER through CertificateFactory, so reaching this at all
     * proves the encoding parses. These check it says what it should.
     */
    @Test
    fun generatesAUsableSelfSignedCertificate() {
        val (key, cert) = SelfSignedCert.generate("Deskflow Android")

        assertEquals("CN=Deskflow Android", cert.subjectX500Principal.name)
        assertEquals("CN=Deskflow Android", cert.issuerX500Principal.name)
        assertEquals("SHA256withRSA", cert.sigAlgName)
        assertEquals("RSA", key.algorithm)
        assertEquals(2048, (cert.publicKey as java.security.interfaces.RSAPublicKey).modulus.bitLength())

        // Signed by its own key, and valid right now.
        cert.verify(cert.publicKey)
        cert.checkValidity()
    }

    /** A second parse from the raw bytes catches anything the first one happened to tolerate. */
    @Test
    fun encodingSurvivesARoundTrip() {
        val (_, cert) = SelfSignedCert.generate("Deskflow Android")
        val again = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(cert.encoded)) as X509Certificate

        assertEquals(cert, again)
        assertTrue(cert.encoded.contentEquals(again.encoded))
    }

    /** Long-form DER lengths: a 2048-bit key pushes the body well past 127 bytes. */
    @Test
    fun usesLongFormLengthsForLargeBodies() {
        val (_, cert) = SelfSignedCert.generate("Deskflow Android")
        val der = cert.encoded

        assertEquals(0x30, der[0].toInt() and 0xFF) // SEQUENCE
        val lengthByte = der[1].toInt() and 0xFF
        assertTrue("expected long-form length, got $lengthByte", lengthByte > 0x80)
        assertEquals(der.size, 2 + (lengthByte and 0x7F) + lengthOf(der, lengthByte))
    }

    private fun lengthOf(der: ByteArray, lengthByte: Int): Int {
        var value = 0
        for (i in 1..(lengthByte and 0x7F)) {
            value = (value shl 8) or (der[1 + i].toInt() and 0xFF)
        }
        return value
    }
}
