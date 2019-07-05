package org.prebid.server.proto.openrtb.ext.request.sonobi;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.sonobi
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpSonobi {

    @JsonProperty("TagID")
    String tagId;
}
