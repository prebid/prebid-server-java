package org.prebid.server.proto.openrtb.ext.request.seedtag;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpSeedtag {

    @JsonProperty("adUnitId")
    String adUnitId;

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("integrationType")
    String integrationType;

}
