package org.prebid.server.proto.openrtb.ext.request.acuity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpAcuityads {

    String host;

    @JsonProperty("accountid")
    String accountId;
}
