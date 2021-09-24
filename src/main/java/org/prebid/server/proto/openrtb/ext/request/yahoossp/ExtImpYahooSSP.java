package org.prebid.server.proto.openrtb.ext.request.yahoossp;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.yahoossp
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpYahooSSP {

    String dcn;

    String pos;
}
