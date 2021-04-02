package org.prebid.server.proto.openrtb.ext.request.colossus;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.colossus
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpColossus {

    @JsonProperty("TagID")
    String tagId;
}
