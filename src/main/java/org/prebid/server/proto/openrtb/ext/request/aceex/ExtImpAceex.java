package org.prebid.server.proto.openrtb.ext.request.aceex;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExtImpAceex(
        @JsonProperty("accountid")
        String accountId) {
}
