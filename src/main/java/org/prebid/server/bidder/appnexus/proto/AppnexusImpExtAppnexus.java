package org.prebid.server.bidder.appnexus.proto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AppnexusImpExtAppnexus {

    Integer placementId;

    String keywords;

    String trafficSourceCode;

    Boolean usePmtRule;

    JsonNode privateSizes;

    String extInvCode;

    String externalImpId;
}
