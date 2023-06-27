package org.prebid.server.proto.openrtb.ext.request.yahooads;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.yahooAds
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpYahooAds {

    String dcn;

    String pos;
}
