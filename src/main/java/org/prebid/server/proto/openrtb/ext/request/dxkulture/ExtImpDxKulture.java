package org.prebid.server.proto.openrtb.ext.request.dxkulture;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpDxKulture {

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("placementId")
    String placementId;
}
