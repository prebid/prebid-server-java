package org.prebid.server.bidder.invibes.model;

import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class InvibesInternalParams {

    InvibesBidParams bidParams;

    Integer domainID;

    Boolean isAMP;

    Boolean gdpr;

    String gdprConsent;

    String testBvid;

    Boolean testLog;
}
