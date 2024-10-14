package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class ThrottlingThresholdsFactory {

    public ThrottlingThresholds create(byte[] bytes, ObjectMapper mapper) throws IOException {
        final JsonNode thresholdsJsonNode = mapper.readTree(bytes);
        return mapper.treeToValue(thresholdsJsonNode, ThrottlingThresholds.class);
    }
}
