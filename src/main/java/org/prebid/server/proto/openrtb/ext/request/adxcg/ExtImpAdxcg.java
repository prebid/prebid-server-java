package org.prebid.server.proto.openrtb.ext.request.adxcg;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.adxcg
 */
@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpAdxcg {

    String adzoneid;
}
