package org.netpreserve.outbackproxy;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v1CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v1CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.bouncycastle.util.io.pem.PemWriter;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static java.time.temporal.ChronoUnit.DAYS;

/**
 * Generates and signs SSL certificates.
 *
 * We use an elliptic curve rather than RSA as its faster to generate and handshake.
 */
class CertificateGenerator {
    private static final char[] BLANK = "".toCharArray();

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private final String caName = "OutbackProxy Web Archive CA";
    private final KeyPair caKeyPair;
    private final X509CertificateHolder caCert;

    CertificateGenerator(Path caKeyFile, Path caCertFile) throws IOException {
        if (caKeyFile != null && Files.exists(caKeyFile)) {
            PrivateKey privateKey = loadKey(caKeyFile);
            caCert = loadCert(caCertFile);
            try {
                PublicKey publicKey = new JcaX509CertificateConverter().getCertificate(caCert).getPublicKey();
                caKeyPair = new KeyPair(publicKey, privateKey);
            } catch (CertificateException e) {
                throw new IOException(e);
            }
        } else {
            caKeyPair = generateKeyPair();
            caCert = selfSign(caName, caKeyPair);
            if (caKeyFile != null) {
                saveKey(caKeyPair.getPrivate(), caKeyFile);
                saveCert(caCert, caCertFile);
            }
        }
    }

    /**
     * Write a private key as a PEM file.
     */
    private static void saveKey(PrivateKey key, Path file) throws IOException {
        try {
            Files.createFile(file, PosixFilePermissions.asFileAttribute(new HashSet<>(Arrays.asList(OWNER_READ, OWNER_WRITE))));
        } catch (UnsupportedOperationException e) {
            // non-posix platform
        }
        try (PemWriter pemWriter = new PemWriter(Files.newBufferedWriter(file, CREATE, WRITE))) {
            pemWriter.writeObject(new PemObject("PRIVATE KEY", key.getEncoded()));
        }
    }

    /**
     * Write a certificate as a PEM file.
     */
    private static void saveCert(X509CertificateHolder holder, Path file) throws IOException {
        try (PemWriter pemWriter = new PemWriter(Files.newBufferedWriter(file, CREATE, WRITE))) {
            pemWriter.writeObject(new PemObject("CERTIFICATE", holder.toASN1Structure().getEncoded()));
        }
    }

    /**
     * Read a certificate from a PEM file.
     */
    private static X509CertificateHolder loadCert(Path caCertFile) throws IOException {
        try (PemReader pemReader = new PemReader(Files.newBufferedReader(caCertFile, US_ASCII))) {
            PemObject object = pemReader.readPemObject();
            if (object == null || !object.getType().equals("CERTIFICATE")) {
                throw new IOException(caCertFile + " does not contain a certificate");
            }
            return new X509CertificateHolder(object.getContent());
        }
    }

    /**
     * Read a private key from a PEM file.
     */
    private static PrivateKey loadKey(Path caKeyFile) throws IOException {
        PrivateKey privateKey;
        try (PemReader pemReader = new PemReader(Files.newBufferedReader(caKeyFile, US_ASCII))) {
            PemObject object = pemReader.readPemObject();
            if (object == null || !object.getType().equals("PRIVATE KEY")) {
                throw new IOException(caKeyFile + " does not contain a private key");
            }
            try {
                privateKey = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(object.getContent()));
            } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
                throw new IOException("failed to parse key from " + caKeyFile, e);
            }
        }
        return privateKey;
    }

    /**
     * Generate a new EC keypair.
     */
    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
            keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new IllegalStateException("Failed to generate key pair", e);
        }
    }

    /**
     * Generate and sign a certificate for a given hostname and return an SSLContext configured to serve it.
     */
    SSLContext contextForHost(String hostname) throws GeneralSecurityException, IOException {
        SSLContext context = SSLContext.getInstance("TLS");
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509", "SunJSSE");
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);
        KeyPair keyPair = caKeyPair; // is it ok to reuse the key?
        X509CertificateHolder holder = sign(caName, caKeyPair.getPrivate(), hostname, keyPair.getPublic());
        java.security.cert.Certificate[] certs = new java.security.cert.Certificate[]{
                new JcaX509CertificateConverter().getCertificate(holder)
        };
        keyStore.setKeyEntry("eckey", keyPair.getPrivate(), BLANK, certs);
        kmf.init(keyStore, BLANK);
        context.init(kmf.getKeyManagers(), null, null);
        return context;
    }

    /**
     * Sign a certificate with a CA key.
     */
    private X509CertificateHolder sign(String issuerName, PrivateKey issuerKey, String subjectName, PublicKey subjectKey) {
        try {
            Instant notBefore = Instant.now().minus(1, DAYS);
            Instant notAfter = Instant.now().plus(100 * 365, DAYS);
            X500Principal subject = new X500Principal("CN=" + subjectName);
            X500Principal issuer = new X500Principal("CN=" + issuerName);
            X509v1CertificateBuilder builder = new JcaX509v1CertificateBuilder(issuer, BigInteger.ONE, Date.from(notBefore),
                    Date.from(notAfter), subject, subjectKey);
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(issuerKey);
            return builder.build(signer);
        } catch (OperatorCreationException e) {
            throw new IllegalStateException("Signing failed", e);
        }
    }

    /**
     * Sign a certificate with its own key.
     */
    private X509CertificateHolder selfSign(String name, KeyPair keyPair) {
        return sign(name, keyPair.getPrivate(), name, keyPair.getPublic());
    }
}
