package org.prebid.server.bidder.mediasquare.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Builder
@Value(staticConstructor = "of")
public class MediasquareCode {

    @JsonProperty("adunit")
    String adUnit;

    @JsonProperty("auctionid")
    String auctionId;

    @JsonProperty("bidid")
    String bidId;

    String code;

    String owner;

    @JsonProperty("mediatypes")
    MediasquareMediaTypes mediaTypes;

    Map<String, MediasquareFloor> floor;
}
