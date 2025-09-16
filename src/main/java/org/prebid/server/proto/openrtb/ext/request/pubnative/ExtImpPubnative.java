package org.prebid.server.proto.openrtb.ext.request.pubnative;

import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.pubnative
 */
@Value(staticConstructor = "of")
public class ExtImpPubnative {

    Integer zoneId;

    String appAuthToken;
}
