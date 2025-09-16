package org.prebid.server.proto.openrtb.ext.request.adxcg;

import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.adxcg
 */
@Value(staticConstructor = "of")
public class ExtImpAdxcg {

    String adzoneid;
}
