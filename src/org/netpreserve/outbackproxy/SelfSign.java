package org.netpreserve.outbackproxy;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v1CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v1CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Date;

import static java.time.temporal.ChronoUnit.DAYS;

/**
 * Generates self-signed SSL certificates.
 *
 * We use an elliptic curve rather than RSA as its faster to generate and handshake.
 */
class SelfSign {
    private static final char[] DUMMY_PASSWORD = "changeit".toCharArray();

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    static SSLContext sslContext() throws GeneralSecurityException, IOException, OperatorCreationException {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(SelfSign.keyManagers(), null, null);
        return context;
    }

    static KeyManager[] keyManagers() throws GeneralSecurityException, IOException, OperatorCreationException {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509", "SunJSSE");
        kmf.init(generateKeyStore(DUMMY_PASSWORD), DUMMY_PASSWORD);
        return kmf.getKeyManagers();

    }

    private static KeyStore generateKeyStore(char[] password) throws GeneralSecurityException, OperatorCreationException, IOException {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);
        generateKeyPair(keyStore, password);
        return keyStore;
    }

    private static void generateKeyPair(KeyStore keyStore, char[] password) throws GeneralSecurityException, OperatorCreationException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        java.security.cert.Certificate[] certs = new java.security.cert.Certificate[]{
                selfSign(keyPair, "SHA256withECDSA")
        };
        keyStore.setKeyEntry("eckey", keyPair.getPrivate(), password, certs);
    }

    private static X509Certificate selfSign(KeyPair keyPair, String algo) throws CertificateException, OperatorCreationException {
        Instant notBefore = Instant.now().minus(1, DAYS);
        Instant notAfter = Instant.now().plus(365, DAYS);
        X500Principal issuer = new X500Principal("CN=Web Archive Proxy Certificate");
        X509v1CertificateBuilder builder = new JcaX509v1CertificateBuilder(issuer, BigInteger.ONE, Date.from(notBefore),
                Date.from(notAfter), issuer, keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder(algo).build(keyPair.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter().getCertificate(holder);
    }
}
