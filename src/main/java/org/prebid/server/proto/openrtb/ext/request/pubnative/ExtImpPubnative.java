package org.prebid.server.proto.openrtb.ext.request.pubnative;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.pubnative
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpPubnative {

    Integer zoneId;

    String appAuthToken;
}
