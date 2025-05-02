package org.prebid.server.proto.openrtb.ext.request.adocean;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.adocean
 */
@Value(staticConstructor = "of")
public class ExtImpAdocean {

    @JsonProperty("emitterPrefix")
    String emitterPrefix;

    @JsonProperty("masterId")
    String masterId;

    @JsonProperty("slaveId")
    String slaveId;
}
