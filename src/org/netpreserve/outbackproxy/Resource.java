package org.netpreserve.outbackproxy;

import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import org.jwat.arc.ArcReader;
import org.jwat.arc.ArcReaderFactory;
import org.jwat.arc.ArcRecordBase;
import org.jwat.common.ByteCountingPushBackInputStream;
import org.jwat.common.HeaderLine;
import org.jwat.common.HttpHeader;
import org.jwat.warc.WarcReader;
import org.jwat.warc.WarcReaderFactory;
import org.jwat.warc.WarcRecord;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Date;

import static io.undertow.util.Headers.CONTENT_TYPE;
import static org.jwat.common.UriProfile.RFC3986_ABS_16BIT_LAX;

class Resource implements Closeable {
    private final Closeable container;
    private final InputStream payloadStream;
    private final Date date;
    private final HeaderMap headers;

    Resource(Closeable container, ByteCountingPushBackInputStream payloadStream, Date date, HeaderMap headers) {
        this.container = container;
        this.payloadStream = payloadStream;
        this.date = date;
        this.headers = headers;
    }

    static Resource fromStream(ByteCountingPushBackInputStream stream) throws IOException {
        if (ArcReaderFactory.isArcRecord(stream)) {
            ArcReader reader = ArcReaderFactory.getReaderUncompressed(stream);
            reader.setUriProfile(RFC3986_ABS_16BIT_LAX);
            ArcRecordBase record = reader.getNextRecord();
            HttpHeader http = record.getHttpHeader();
            return new Resource(stream, http.getPayloadInputStream(), record.getArchiveDate(), convertHeader(http));
        } else if (WarcReaderFactory.isWarcRecord(stream)) {
            WarcReader reader = WarcReaderFactory.getReaderUncompressed(stream);
            reader.setUriProfile(RFC3986_ABS_16BIT_LAX);
            WarcRecord record = reader.getNextRecord();
            HttpHeader http = record.getHttpHeader();
            if (http != null) { // response record
                return new Resource(stream, http.getPayloadInputStream(), record.header.warcDate, convertHeader(http));
            } else { // resource record
                HeaderMap headerMap = new HeaderMap();
                if (record.header.contentTypeStr != null) {
                    headerMap.put(CONTENT_TYPE, record.header.contentTypeStr);
                }
                return new Resource(stream, record.getPayload().getInputStream(), record.header.warcDate, headerMap);
            }
        } else {
            throw new IllegalArgumentException("Not a WARC or ARC record");
        }
    }

    /**
     * Converts a jwat HttpHeader into an Undertow HeaderMap.
     */
    private static HeaderMap convertHeader(HttpHeader header) {
        HeaderMap map = new HeaderMap();
        for (HeaderLine line: header.getHeaderList()) {
            map.put(HttpString.tryFromString(line.name), line.value);
        }
        return map;
    }

    InputStream payload() {
        return payloadStream;
    }

    HeaderMap headers() {
        return headers;
    }

    public void close() throws IOException {
        container.close();
    }

    Instant instant() {
        return date.toInstant();
    }
}
