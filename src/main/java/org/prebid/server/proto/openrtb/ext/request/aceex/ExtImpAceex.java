package org.prebid.server.proto.openrtb.ext.request.aceex;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAceex {

    @JsonProperty("accountid")
    String accountId;
}
