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

    Integer ttlseconds;

    String aid;

    String key;

    String bidid; // this is "/vtrack" specific

    String bidder; // this is "/vtrack" specific

    Long timestamp; // this is "/vtrack" specific
}
