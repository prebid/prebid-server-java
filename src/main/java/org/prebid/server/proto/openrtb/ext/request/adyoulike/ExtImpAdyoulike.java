package org.prebid.server.proto.openrtb.ext.request.adyoulike;

import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.adyoulike
 */
@Value(staticConstructor = "of")
public class ExtImpAdyoulike {

    String placement;

    String campaign;

    String track;

    String creative;

    String source;

    String debug;
}
