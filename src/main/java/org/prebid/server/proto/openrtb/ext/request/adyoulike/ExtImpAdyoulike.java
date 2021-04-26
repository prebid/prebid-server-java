package org.prebid.server.proto.openrtb.ext.request.adyoulike;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.adyoulike
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpAdyoulike {

    String placement;

    String campaign;

    String track;

    String creative;

    String source;

    String debug;
}
