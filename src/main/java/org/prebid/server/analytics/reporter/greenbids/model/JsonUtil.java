package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonUtil {
    private static final ObjectMapper mapper = new ObjectMapper();
    public static String toJson(Object obj) throws Exception {
        return mapper.writeValueAsString(obj);
    }
}
