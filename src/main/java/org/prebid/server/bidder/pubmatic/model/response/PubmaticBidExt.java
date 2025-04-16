package org.prebid.server.bidder.pubmatic.model.response;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class PubmaticBidExt {

    @JsonProperty("BidType")
    @JsonAlias({"bidtype", "bidType"})
    Integer bidType;

    VideoCreativeInfo video;

    @JsonProperty("prebiddealpriority")
    Integer prebidDealPriority;
}
