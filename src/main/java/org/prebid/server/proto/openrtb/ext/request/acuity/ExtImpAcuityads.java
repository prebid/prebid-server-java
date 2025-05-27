package org.prebid.server.proto.openrtb.ext.request.acuity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAcuityads {

    String host;

    @JsonProperty("accountid")
    String accountId;
}
