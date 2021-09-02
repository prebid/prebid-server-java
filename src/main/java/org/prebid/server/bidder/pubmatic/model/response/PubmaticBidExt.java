package org.prebid.server.bidder.pubmatic.model.response;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class PubmaticBidExt {

    @JsonProperty("BidType")
    @JsonAlias({"bidtype", "bidType"})
    Integer bidType;

    VideoCreativeInfo video;
}
