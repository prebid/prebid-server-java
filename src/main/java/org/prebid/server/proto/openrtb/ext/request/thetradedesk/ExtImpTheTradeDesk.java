package org.prebid.server.proto.openrtb.ext.request.thetradedesk;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpTheTradeDesk {

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("supplySourceId")
    String supplySourceId;
}
