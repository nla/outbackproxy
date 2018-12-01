package org.netpreserve.outbackproxy;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.connector.ByteBufferPool;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.ExceptionHandler;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.HttpString;

import javax.net.ssl.SSLContext;
import java.io.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static io.undertow.util.Headers.*;
import static java.time.ZoneOffset.UTC;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

public class OutbackProxy {

    private static final HttpString ACCEPT_DATETIME = new HttpString("Accept-Datetime");
    private static final HttpString MEMENTO_DATETIME = new HttpString("Memento-Datetime");
    private static final Set<HttpString> HEADERS_TO_RENAME = new HashSet<>(Arrays.asList(
            TRANSFER_ENCODING, DATE, CONNECTION, SERVER
    ));

    private final CaptureIndex captureIndex;
    private final ResourceStore resourceStore;
    private final Undertow webServer;

    public static void main(String args[]) throws Exception {
        Map<String, String> env = System.getenv();
        String host = env.getOrDefault("HOST", "0.0.0.0");
        int port = Integer.parseInt(env.getOrDefault("PORT", "3128"));
        String cdxServerUrl = env.getOrDefault("CDX_URL", "http://localhost:9901/myindex");
        String warcServerUrl = env.getOrDefault("WARC_URL", "");
        CaptureIndex captureIndex = new CaptureIndex(cdxServerUrl);
        ResourceStore resourceStore = new ResourceStore(warcServerUrl);
        new OutbackProxy(host, port, captureIndex, resourceStore).run();
    }

    public OutbackProxy(String host, int port, CaptureIndex captureIndex, ResourceStore resourceStore) throws Exception {
        this.captureIndex = captureIndex;
        this.resourceStore = resourceStore;
        SSLContext sslContext = SelfSign.sslContext();
        ByteBufferPool bufferPool = new DefaultByteBufferPool(true, 16 * 1024 - 20, -1, 4);
        HttpHandler handler = this::handleRequest;
        handler = Handlers.exceptionHandler(handler).addExceptionHandler(Exception.class, this::handleException);
        handler = new BlockingHandler(handler);
        handler = new SSLConnectHandler(handler, handler, sslContext, bufferPool);
        webServer = Undertow.builder()
                .addHttpListener(port, host)
                .setByteBufferPool(bufferPool)
                .setHandler(handler)
                .build();
    }

    private void run() {
        webServer.start();
    }

    /**
     * Handle a proxy request from a client.
     */
    private void handleRequest(HttpServerExchange exchange) throws IOException {
        String url = exchange.getRequestURL();
        if (exchange.getQueryString() != null) {
            url += "?" + exchange.getQueryString();
        }
        Instant requestedTime = parseRequestedTime(exchange);
        Capture capture = captureIndex.findClosest(url, requestedTime);
        if (capture == null) {
            exchange.setStatusCode(404);
            exchange.getResponseSender().send("Not in archive");
            return;
        }
        try (Resource resource = resourceStore.open(capture.filename(), capture.offset(), capture.compressedLength())) {
            sendResponse(exchange, resource);
        }
    }

    private void handleException(HttpServerExchange exchange) throws IOException {
        Exception ex = (Exception) exchange.getAttachment(ExceptionHandler.THROWABLE);
        exchange.setStatusCode(500);
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        exchange.getRequestHeaders().put(CONTENT_TYPE, "text/plain");
        exchange.getResponseSender().send(sw.toString());
    }

    /**
     * Parse a Memento style Accept-Datetime request header.
     */
    private Instant parseRequestedTime(HttpServerExchange exchange) {
        String time = exchange.getRequestHeaders().getFirst(ACCEPT_DATETIME);
        if (time == null) {
            return Instant.ofEpochSecond(1);
        }
        return RFC_1123_DATE_TIME.parse(time, Instant::from);
    }

    /**
     * Send a HTTP payload to the client.
     */
    private void sendResponse(HttpServerExchange exchange, Resource resource) throws IOException {
        HeaderMap headers = exchange.getResponseHeaders();
        for (HeaderValues values : resource.headers()) {
            HttpString name = values.getHeaderName();
            if (HEADERS_TO_RENAME.contains(name)) {
                name = new HttpString("X-Archive-Orig-" + name);
            }
            headers.putAll(values.getHeaderName(), values);
        }
        headers.put(MEMENTO_DATETIME, RFC_1123_DATE_TIME.format(resource.instant().atOffset(UTC)));
        headers.add(VARY, "accept-datetime");
        OutputStream output = exchange.getOutputStream();
        copyStream(resource.payload(), output);
        output.close();
    }

    private static void copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        for (int n = input.read(buffer); n >= 0; n = input.read(buffer)) {
            output.write(buffer, 0, n);
        }
    }
}
