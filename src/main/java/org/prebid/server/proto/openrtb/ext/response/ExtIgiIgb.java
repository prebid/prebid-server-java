package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExtIgiIgb {

    String origin;

    Double maxbid;

    @Builder.Default
    String cur = "USD";

    JsonNode pbs;

    ObjectNode ps;
}
