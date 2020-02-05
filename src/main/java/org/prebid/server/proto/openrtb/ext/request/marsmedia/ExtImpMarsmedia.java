package org.prebid.server.proto.openrtb.ext.request.marsmedia;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.marsmedia
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpMarsmedia {

    String zone;
}
