package org.prebid.server.proto.openrtb.ext.request.adf;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.adf
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpAdf {

    String mid;
}
