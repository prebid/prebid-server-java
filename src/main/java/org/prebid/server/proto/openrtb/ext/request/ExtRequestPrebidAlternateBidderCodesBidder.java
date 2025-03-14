package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.Set;

@Builder(toBuilder = true)
@Value
public class ExtRequestPrebidAlternateBidderCodesBidder implements AlternateBidder {

    Boolean enabled;

    @JsonProperty("allowedbiddercodes")
    Set<String> allowedBidderCodes;
}
