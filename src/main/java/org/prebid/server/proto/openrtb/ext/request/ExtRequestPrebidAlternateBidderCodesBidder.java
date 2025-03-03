package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Builder;
import lombok.Value;

import java.util.Set;

@Builder(toBuilder = true)
@Value
public class ExtRequestPrebidAlternateBidderCodesBidder {

    Boolean enabled;

    @JsonAlias("allowedbiddercodes")
    Set<String> allowedBidderCodes;
}
