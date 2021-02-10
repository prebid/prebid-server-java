package org.prebid.server.proto.openrtb.ext.request.lunamedia;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.lunamedia
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpLunamedia {

    String pubid;

    String placement;
}
