package org.prebid.server.proto.openrtb.ext.request.gamma;

import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.gamma
 */
@Value(staticConstructor = "of")
public class ExtImpGamma {

    String id;

    String zid;

    String wid;
}
