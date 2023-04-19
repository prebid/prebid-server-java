package org.prebid.server.bidder.huaweiads.model.request;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Value;

@Value(staticConstructor = "of")
public class CellInfo {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String mcc;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String mnc;
}
