package org.prebid.server.cache.proto.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class PutObject {

    String type;

    JsonNode value;

    Integer expiry;
}
