package org.netpreserve.outbackproxy;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

class Capture {
    static final DateTimeFormatter ARC_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("GMT"));

    private final String[] fields;

    Capture(String cdxLine) {
        this.fields = cdxLine.split(" ");
    }

    public Instant time() {
        return ARC_TIME.parse(fields[1], Instant::from);
    }

    long compressedLength() {
        if (fields.length < 11 || fields[8].equals("-")) {
            return -1;
        }
        return Long.parseLong(fields[8]);
    }

    long offset() {
        return Long.parseLong(fields[fields.length - 2]);
    }

    String filename() {
        return fields[fields.length - 1];
    }
}
