package org.prebid.server.bidder.adnuntius.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class AdnuntiusRequest {

    @JsonProperty("adUnits")
    List<AdnuntiusAdUnit> adUnits;

    @JsonProperty("metaData")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    AdnuntiusMetaData metaData;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String context;

    @JsonProperty("kv")
    ObjectNode keyValue;
}
