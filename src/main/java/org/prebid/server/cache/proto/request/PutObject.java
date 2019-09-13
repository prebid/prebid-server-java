package org.prebid.server.cache.proto.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class PutObject {

    String type;

    JsonNode value;

    Integer expiry;

    String bidid;

    String bidder;

    Integer ttlseconds;
}

