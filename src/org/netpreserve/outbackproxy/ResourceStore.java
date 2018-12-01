                package org.netpreserve.outbackproxy;

import org.jwat.common.ByteCountingPushBackInputStream;
import org.jwat.gzip.GzipReader;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;

import static java.nio.file.StandardOpenOption.READ;

class ResourceStore {
    private String baseUrl;

    ResourceStore(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    Resource open(String filename, long offset, long length) throws IOException {
        ByteCountingPushBackInputStream stream = openStream(filename, offset, length);
        try {
            return Resource.fromStream(stream);
        } catch (IOException | RuntimeException e) {
            stream.close();
            throw e;
        }
    }

    /**
     * Send a HTTP range request for the possibly-compressed record.
     */
    private ByteCountingPushBackInputStream openStream(String filename, long offset, long length) throws IOException {
        String urlOrPath = baseUrl + filename;
        if (urlOrPath.startsWith("/")) {
            return openLocalStream(Paths.get(filename), offset);
        }
        URL url = new URL(urlOrPath);
        if (url.getProtocol().equalsIgnoreCase("http") || url.getProtocol().equalsIgnoreCase("https")) {
            return openRemoteStream(url, offset, length);
        } else if (url.getProtocol().equalsIgnoreCase("file")) {
            return openLocalStream(Paths.get(url.getPath()), offset);
        } else {
            throw new IllegalArgumentException("unsupported url scheme: " + url.getAuthority());
        }
    }

    private ByteCountingPushBackInputStream openLocalStream(Path path, long offset) throws IOException {
        FileChannel channel = FileChannel.open(path, READ);
        try {
            if (offset != 0) {
                channel.position(offset);
            }
            return new ByteCountingPushBackInputStream(Channels.newInputStream(channel), 32);
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    /**
     * Send a HTTP range request for the possibly-compressed record.
     */
    private ByteCountingPushBackInputStream openRemoteStream(URL url, long offset, long length) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (length >= 0) {
            conn.setRequestProperty("Range", "bytes=" + offset + "-" + (offset + length + 1));
        } else { // compressed length is unknown: get all
            conn.setRequestProperty("Range", "bytes=" + offset + "-");
        }
        ByteCountingPushBackInputStream stream = new ByteCountingPushBackInputStream(conn.getInputStream(), 32);
        try {
            if (GzipReader.isGzipped(stream)) {
                return new ByteCountingPushBackInputStream(new GZIPInputStream(stream, 8192), 32);
            }
            return stream;
        } catch (IOException | RuntimeException e) {
            stream.close();
            throw e;
        }
    }
}
