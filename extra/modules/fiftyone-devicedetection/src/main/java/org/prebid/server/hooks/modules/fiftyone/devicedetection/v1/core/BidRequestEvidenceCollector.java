package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import com.iab.openrtb.request.BidRequest;

@FunctionalInterface
public interface BidRequestEvidenceCollector extends EvidenceCollector<BidRequest> {
}
