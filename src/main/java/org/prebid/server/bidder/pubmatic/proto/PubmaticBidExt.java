package org.prebid.server.bidder.pubmatic.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class PubmaticBidExt {
    @JsonProperty("BidType")
    Integer bidType;

    VideoCreativeInfo video;
}
