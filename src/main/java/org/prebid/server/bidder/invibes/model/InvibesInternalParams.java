package org.prebid.server.bidder.invibes.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class InvibesInternalParams {

    InvibesBidParams bidParams;

    Integer domainId;

    Boolean isAmp;

    Boolean gdpr;

    String gdprConsent;

    String testBvid;

    Boolean testLog;
}
