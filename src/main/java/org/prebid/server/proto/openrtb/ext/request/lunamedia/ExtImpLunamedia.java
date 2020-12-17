package org.prebid.server.proto.openrtb.ext.request.lunamedia;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.lunamedia
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpLunamedia {

    @JsonProperty("pubid")
    String pubId;

    String placement;
}
