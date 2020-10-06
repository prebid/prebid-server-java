package org.prebid.server.proto.openrtb.ext.request.zeroclickfraud;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.zeroclickfraud
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpZeroclickfraud {

    @JsonProperty("sourceId")
    Integer sourceId;

    String host;
}
