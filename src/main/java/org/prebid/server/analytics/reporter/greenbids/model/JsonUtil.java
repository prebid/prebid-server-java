package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonUtil {

    private JsonUtil() {
        throw new UnsupportedOperationException("Utility class and cannot be instantiated");
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static String toJson(Object obj) throws Exception {
        return OBJECT_MAPPER.writeValueAsString(obj);
    }
}
