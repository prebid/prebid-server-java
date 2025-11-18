package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.prebid.server.auction.aliases.AlternateBidder;

import java.util.Set;

@Value(staticConstructor = "of")
public class ExtRequestPrebidAlternateBidderCodesBidder implements AlternateBidder {

    Boolean enabled;

    @JsonProperty("allowedbiddercodes")
    Set<String> allowedBidderCodes;
}
