package org.prebid.server.proto.openrtb.ext.request.stroeercore;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.stroeerCore
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpStroeerCore {

    @JsonProperty("sid")
    String slotId;
}

