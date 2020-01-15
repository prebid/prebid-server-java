package org.prebid.server.proto.openrtb.ext.request.datablocks;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.datablocks
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpDatablocks {

    @JsonProperty("sourceId")
    Integer sourceId;

    String host;
}
