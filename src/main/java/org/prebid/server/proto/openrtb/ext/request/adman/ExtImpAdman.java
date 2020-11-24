package org.prebid.server.proto.openrtb.ext.request.adman;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.adman
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpAdman {

    @JsonProperty("TagID")
    String tagId;
}
