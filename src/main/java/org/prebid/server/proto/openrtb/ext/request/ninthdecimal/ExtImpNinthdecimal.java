package org.prebid.server.proto.openrtb.ext.request.ninthdecimal;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.ninthdecimal
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpNinthdecimal {

    String pubid;

    String placement;
}
