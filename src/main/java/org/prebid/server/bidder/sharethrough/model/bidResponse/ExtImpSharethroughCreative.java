package org.prebid.server.bidder.sharethrough.model.bidResponse;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Builder
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpSharethroughCreative {
    String auctionWinId;

    BigDecimal cpm;

    @JsonProperty("creative")
    ExtImpSharethroughCreativeMetadata metadata;

    int version;

}
