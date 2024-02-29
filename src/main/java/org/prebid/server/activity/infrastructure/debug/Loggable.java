package org.prebid.server.activity.infrastructure.debug;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public interface Loggable {

    JsonNode asLogEntry(ObjectMapper mapper);
}
