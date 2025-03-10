package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.Set;

@Builder(toBuilder = true)
@Value
public class ExtRequestPrebidAlternateBidderCodesBidder {

    Boolean enabled;

    @JsonProperty("allowedbiddercodes")
    @JsonAlias("allowed-bidder-codes")
    Set<String> allowedBidderCodes;
}
