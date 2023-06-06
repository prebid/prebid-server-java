package org.prebid.server.proto.openrtb.ext.request.yahooadvertising;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.yahooAdvertising
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpYahooAdvertising {

    String dcn;

    String pos;
}
