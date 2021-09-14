package org.prebid.server.proto.openrtb.ext.request.aceex;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpAceex {

    @JsonProperty("accountid")
    String accountId;
}
