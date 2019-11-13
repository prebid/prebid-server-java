package org.prebid.server.bidder.sharethrough.model.bidresponse;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpSharethroughCreative {

    @JsonProperty("auctionWinId")
    String auctionWinId;

    BigDecimal cpm;

    @JsonProperty("creative")
    ExtImpSharethroughCreativeMetadata metadata;
}

