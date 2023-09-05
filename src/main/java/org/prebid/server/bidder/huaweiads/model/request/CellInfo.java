package org.prebid.server.bidder.huaweiads.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class CellInfo {

    @JsonProperty("mcc")
    String mcc;

    @JsonProperty("mnc")
    String mnc;
}
