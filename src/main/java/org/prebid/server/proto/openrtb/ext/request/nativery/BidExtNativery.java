package org.prebid.server.proto.openrtb.ext.request.nativery;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class BidExtNativery {

    String bidAdMediaType;

    List<String> bidAdvDomains;
}
