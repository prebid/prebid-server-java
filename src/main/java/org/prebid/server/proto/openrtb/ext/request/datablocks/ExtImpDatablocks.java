package org.prebid.server.proto.openrtb.ext.request.datablocks;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.datablocks
 */
@Value(staticConstructor = "of")
public class ExtImpDatablocks {

    @JsonProperty("sourceId")
    Integer sourceId;
}
