package org.prebid.server.proto.openrtb.ext.request.trustx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpTrustxData {

    @JsonProperty("pbadslot")
    String pbAdSlot;

    @JsonProperty("adserver")
    ExtImpTrustxDataAdServer adServer;
}
