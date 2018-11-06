package org.prebid.server.proto.openrtb.ext.request.brightroll;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.brightroll
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpBrightroll {

    String publisher;
}
