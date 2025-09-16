package org.prebid.server.proto.openrtb.ext.request.lunamedia;

import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.lunamedia
 */
@Value(staticConstructor = "of")
public class ExtImpLunamedia {

    String pubid;

    String placement;
}
