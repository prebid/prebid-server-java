package org.prebid.server.proto.openrtb.ext.request.adprime;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.adprime
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpAdprime {

    @JsonProperty("TagID")
    String tagId;
}
