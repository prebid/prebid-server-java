package org.prebid.server.bidder.yieldlab.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.response.ExtBidDsa;

@Value(staticConstructor = "of")
public class YieldlabBid {

    Long id;

    Double price;

    String advertiser;

    @JsonProperty("adsize")
    String adSize;

    Long pid;

    Long did;

    String pvid;

    ExtBidDsa dsa;
}
