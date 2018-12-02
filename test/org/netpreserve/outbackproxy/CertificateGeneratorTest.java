package org.netpreserve.outbackproxy;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

import static org.junit.Assert.assertTrue;

public class CertificateGeneratorTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void persist() throws IOException, GeneralSecurityException {
        Path root = folder.newFolder().toPath();
        Path keyFile = root.resolve("key.pem");
        Path certFile = root.resolve("cert.pem");
        CertificateGenerator certgen = new CertificateGenerator(keyFile, certFile);
        assertTrue(Files.exists(keyFile));
        assertTrue(Files.exists(certFile));
        certgen.contextForHost("hello.test");
        new CertificateGenerator(keyFile, certFile).contextForHost("hello.test");
    }

    @Test
    public void ephemeral() throws IOException, GeneralSecurityException {
        CertificateGenerator certgen = new CertificateGenerator(null, null);
        certgen.contextForHost("hello.test");
    }
}