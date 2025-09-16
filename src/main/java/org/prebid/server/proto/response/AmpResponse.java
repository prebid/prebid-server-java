package org.prebid.server.proto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

import java.util.Map;

@Value(staticConstructor = "of")
public class AmpResponse {

    Map<String, JsonNode> targeting;

    ExtAmpVideoResponse ext;
}
