package org.prebid.server.proto.openrtb.ext.request.riptide;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Defines the contract for bidrequest.imp[i].ext.prebid.bidder.riptide (and for every alias of it):
 * the publisher's and the placement's public ids, and an optional floor in USD. The serving host is
 * not a param: it is the bidder's endpoint in its bidder-config.
 */
@Value(staticConstructor = "of")
public class ExtImpRiptide {

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("bidfloor")
    BigDecimal bidfloor;
}
