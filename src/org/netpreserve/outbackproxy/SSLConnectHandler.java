package org.netpreserve.outbackproxy;

import io.undertow.connector.ByteBufferPool;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.protocol.http.HttpOpenListener;
import io.undertow.util.Methods;
import org.xnio.OptionMap;
import org.xnio.StreamConnection;
import org.xnio.ssl.SslConnection;

import javax.net.ssl.SSLContext;

/**
 * Handles the HTTP CONNECT method by establishing an SSL session.
 */
class SSLConnectHandler implements HttpHandler {
    private final HttpHandler handler;
    private final HttpHandler next;
    private final SSLContext sslContext;
    private final ByteBufferPool byteBufferPool;

    SSLConnectHandler(HttpHandler handler, HttpHandler next, SSLContext sslContext, ByteBufferPool byteBufferPool) {
        this.handler = handler;
        this.next = next;
        this.sslContext = sslContext;
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
        UndertowXnioSsl xnioSsl = new UndertowXnioSsl(connection.getWorker().getXnio(), OptionMap.EMPTY, sslContext);
        SslConnection sslConnection = xnioSsl.wrapExistingConnection(connection, OptionMap.EMPTY);
        UndertowXnioSsl.getSslEngine(sslConnection).setUseClientMode(false);
        HttpOpenListener httpOpenListener = new HttpOpenListener(byteBufferPool, OptionMap.EMPTY);
        httpOpenListener.setRootHandler(handler);
        httpOpenListener.handleEvent(sslConnection);
    }
}
