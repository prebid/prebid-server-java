package org.prebid.server.proto.openrtb.ext.request.aps;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines bidrequest.imp[i].ext.prebid.bidder.aps.
 */
@Value(staticConstructor = "of")
public class ExtImpAps {

    @JsonProperty("accountID")
    String accountID;

    @JsonProperty("region")
    String region;
}
