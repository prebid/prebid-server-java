package org.prebid.server.bidder.tappx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@Value
@AllArgsConstructor(staticName = "of")
public class TapxBidderExt {

    @JsonProperty("tappxkey")
    String tappxKey;

    @JsonProperty("mktag")
    String mkTag;

    List<String> bcid;

    List<String> bcrid;
}
