package org.prebid.server.bidder.msft.proto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MsftExtImpOutgoing {

    Integer placementId;

    Boolean allowSmallerSizes;

    Boolean usePmtRule;

    String keywords;

    String trafficSourceCode;

    String pubClick;

    String extInvCode;

    String extImpId;
}
