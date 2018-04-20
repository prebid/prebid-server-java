package org.prebid.server.bidder.appnexus.proto;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class AppnexusImpExtAppnexus {

    Integer placementId;

    String keywords;

    String trafficSourceCode;

    Boolean usePmtRule;

    ObjectNode privateSizes; // At this time we do no processing on the private sizes, so just leaving it as a JSON blob
}
