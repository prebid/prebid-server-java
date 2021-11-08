package org.prebid.server.bidder.appnexus.proto;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor(staticName = "of")
public class AppnexusImpExtAppnexus {

    Integer placementId;

    String keywords;

    String trafficSourceCode;

    Boolean usePmtRule;

    ObjectNode privateSizes; // At this time we do no processing on the private sizes, so just leaving it as a JSON blob
}
