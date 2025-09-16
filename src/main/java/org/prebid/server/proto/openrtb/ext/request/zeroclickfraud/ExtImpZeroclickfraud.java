package org.prebid.server.proto.openrtb.ext.request.zeroclickfraud;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.zeroclickfraud
 */
@Value(staticConstructor = "of")
public class ExtImpZeroclickfraud {

    @JsonProperty("sourceId")
    Integer sourceId;

    String host;
}
