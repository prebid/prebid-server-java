package org.prebid.server.proto.openrtb.ext.request.yahooads;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpYahooAds {

    String dcn;

    String pos;
}
