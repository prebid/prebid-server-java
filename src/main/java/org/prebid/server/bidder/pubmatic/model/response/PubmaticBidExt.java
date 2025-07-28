package org.prebid.server.bidder.pubmatic.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class PubmaticBidExt {

    VideoCreativeInfo video;

    @JsonProperty("prebiddealpriority")
    Integer prebidDealPriority;

    String marketplace;

    @JsonProperty("ibv")
    Boolean inBannerVideo;
}
