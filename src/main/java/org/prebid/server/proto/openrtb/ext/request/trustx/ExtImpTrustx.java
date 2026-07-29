package org.prebid.server.proto.openrtb.ext.request.trustx;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;

@Value
@Builder(toBuilder = true)
public class ExtImpTrustx {

    ExtImpPrebid prebid;

    JsonNode bidder;

    ExtImpTrustxData data;

    String gpid;
}
