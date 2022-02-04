package org.prebid.server.bidder.adnuntius.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class AdnuntiusRequest {

    @JsonProperty("adUnits")
    List<AdnuntiusAdUnit> adUnits;

    @JsonProperty("metaData")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    AdnuntiusMetaData metaData;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String context;
}
