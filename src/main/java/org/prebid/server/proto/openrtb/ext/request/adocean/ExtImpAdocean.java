package org.prebid.server.proto.openrtb.ext.request.adocean;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.adocean
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpAdocean {

    @JsonProperty("emiter")
    String emitterDomain;

    @JsonProperty("masterId")
    String masterId;

    @JsonProperty("slaveId")
    String slaveId;
}
