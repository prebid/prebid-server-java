package org.prebid.server.proto.openrtb.ext.request.unruly;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.unruly
 */
@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpUnruly {

    String uuid;

    @JsonProperty("siteid")
    String siteId;
}
