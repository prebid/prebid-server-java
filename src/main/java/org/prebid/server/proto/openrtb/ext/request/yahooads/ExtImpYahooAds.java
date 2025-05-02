package org.prebid.server.proto.openrtb.ext.request.yahooads;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpYahooAds {

    String dcn;

    String pos;
}
