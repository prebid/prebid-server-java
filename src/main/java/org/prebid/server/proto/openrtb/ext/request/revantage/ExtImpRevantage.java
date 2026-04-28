package org.prebid.server.proto.openrtb.ext.request.revantage;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Bidder-specific portion of imp.ext.bidder for the Revantage adapter.
 *
 * <p>{@code feedId} is required. {@code placementId} and {@code publisherId}
 * are optional pass-through identifiers.
 */
@Value(staticConstructor = "of")
public class ExtImpRevantage {

    @JsonProperty("feedId")
    String feedId;

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("publisherId")
    String publisherId;
}
