package org.prebid.server.proto.openrtb.ext.request.acuity;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExtImpAcuityads(
        String host,
        @JsonProperty("accountid")
        String accountId) {
}
