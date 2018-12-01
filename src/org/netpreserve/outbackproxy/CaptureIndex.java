package org.netpreserve.outbackproxy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.time.Instant;

import static java.nio.charset.StandardCharsets.UTF_8;

class CaptureIndex {
    private final String serverUrl;

    CaptureIndex(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    Capture findClosest(String url, Instant time) throws IOException {
        try {
            StringBuilder sb = new StringBuilder(serverUrl);
            sb.append("?url=");
            sb.append(URLEncoder.encode(url, "UTF-8"));
            if (time != null) {
                sb.append("&closest=").append(Capture.ARC_TIME.format(time));
                sb.append("&sort=closest&matchType=exact&limit=1");
            }
            URL queryUrl = new URL(sb.toString());
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(queryUrl.openStream(), UTF_8))) {
                String line = reader.readLine();
                if (line == null) {
                    return null;
                }
                return new Capture(line);
            }
        } catch (UnsupportedEncodingException | MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }
}
