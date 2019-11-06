package org.prebid.server.proto.openrtb.ext.request.gamma;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.gamma
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpGamma {

    String id;

    String zid;

    String wid;
}

