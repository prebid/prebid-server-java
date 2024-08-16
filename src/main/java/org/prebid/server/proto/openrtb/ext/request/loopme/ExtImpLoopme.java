package org.prebid.server.proto.openrtb.ext.request.loopme;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.loopme
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpLoopme {

    String publisherId;
    String bundleId;
    String placementId;
}
