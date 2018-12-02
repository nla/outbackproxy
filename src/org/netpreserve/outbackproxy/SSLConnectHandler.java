package org.netpreserve.outbackproxy;

import io.undertow.connector.ByteBufferPool;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.protocol.http.HttpOpenListener;
import io.undertow.util.Methods;
import org.bouncycastle.operator.OperatorCreationException;
import org.xnio.OptionMap;
import org.xnio.StreamConnection;
import org.xnio.ssl.SslConnection;

import javax.net.ssl.*;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Handles the HTTP CONNECT method by establishing an SSL session.
 */
class SSLConnectHandler implements HttpHandler {
    private final HttpHandler handler;
    private final HttpHandler next;
    private final CertificateGenerator certificateGenerator;
    private final ByteBufferPool byteBufferPool;

    SSLConnectHandler(HttpHandler handler, HttpHandler next, CertificateGenerator certificateGenerator, ByteBufferPool byteBufferPool) {
        this.handler = handler;
        this.next = next;
        this.certificateGenerator = certificateGenerator;
        this.byteBufferPool = byteBufferPool;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (exchange.getRequestMethod().equals(Methods.CONNECT)) {
            exchange.acceptConnectRequest(this::connected);
        } else {
            next.handleRequest(exchange);
        }
    }

    private void connected(StreamConnection connection, HttpServerExchange exchange) {
        UndertowXnioSsl xnioSsl = null;
        try {
            xnioSsl = new UndertowXnioSsl(connection.getWorker().getXnio(), OptionMap.EMPTY, certificateGenerator.contextForHost(exchange.getHostName()));
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
        SslConnection sslConnection = xnioSsl.wrapExistingConnection(connection, OptionMap.EMPTY);
        SSLEngine sslEngine = UndertowXnioSsl.getSslEngine(sslConnection);
        sslEngine.setUseClientMode(false);
        SSLParameters params = sslEngine.getSSLParameters();
        sslEngine.setSSLParameters(params);
        HttpOpenListener httpOpenListener = new HttpOpenListener(byteBufferPool, OptionMap.EMPTY);
        httpOpenListener.setRootHandler(handler);
        httpOpenListener.handleEvent(sslConnection);
    }
}
