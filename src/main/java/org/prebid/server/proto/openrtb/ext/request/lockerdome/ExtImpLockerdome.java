package org.prebid.server.proto.openrtb.ext.request.lockerdome;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.lockerdome
 */
@Value(staticConstructor = "of")
public class ExtImpLockerdome {

    @JsonProperty("adUnitId")
    String adUnitId;
}
