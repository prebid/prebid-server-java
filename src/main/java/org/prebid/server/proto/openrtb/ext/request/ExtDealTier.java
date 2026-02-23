package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.prebid.bidder.dealTier
 */
@Value(staticConstructor = "of")
public class ExtDealTier {

    String prefix;

    @JsonProperty("minDealTier")
    Integer minDealTier;
}
