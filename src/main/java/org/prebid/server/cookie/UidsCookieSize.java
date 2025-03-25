package org.prebid.server.cookie;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.http.Cookie;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.json.ObjectMapperProvider;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class UidsCookieSize {

    // {"tempUIDs":{},"optout":false}
    private static final int TEMP_UIDS_BASE64_BYTES = "eyJ0ZW1wVUlEcyI6e30sIm9wdG91dCI6ZmFsc2V9".length();
    private static final int UID_TEMPLATE_BYTES;

    static {
        try {
            UID_TEMPLATE_BYTES = "\"\":{\"uid\":\"\",\"expires\":\"%s\"},"
                    .formatted(ObjectMapperProvider.mapper().writeValueAsString(
                            ZonedDateTime.ofInstant(Instant.ofEpochSecond(0, 1), ZoneId.of("UTC"))))
                    .length();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private final int cookieSchemaSize;
    private final int maxSize;
    private int encodedUidsSize;

    public UidsCookieSize(int cookieSchemaSize, int maxSize) {
        this.cookieSchemaSize = cookieSchemaSize;
        this.maxSize = maxSize;

        encodedUidsSize = 0;
    }

    public static int schemaSize(Cookie cookieSchema) {
        return cookieSchema.setValue(StringUtils.EMPTY).encode().length();
    }

    public boolean isValid() {
        return maxSize <= 0 || totalSize() <= maxSize;
    }

    public int totalSize() {
        return cookieSchemaSize
                + TEMP_UIDS_BASE64_BYTES
                + Base64Size.base64Size(encodedUidsSize);
    }

    public void addUid(String cookieFamily, String uid) {
        final int uidSize = UID_TEMPLATE_BYTES + cookieFamily.length() + uid.length();
        encodedUidsSize = Base64Size.encodeSize(Base64Size.decodeSize(encodedUidsSize) + uidSize);
    }

    private static class Base64Size {

        public static int encodeSize(int size) {
            return size / 3 * 4 + size % 3;
        }

        public static int decodeSize(int encodedSize) {
            return encodedSize / 4 * 3 + encodedSize % 4;
        }

        private static int base64Size(int encodedSize) {
            return (encodedSize & -4) + 4 * Integer.signum(encodedSize % 4);
        }
    }
}
