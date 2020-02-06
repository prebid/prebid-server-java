package org.prebid.server.bidder.appnexus.proto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;

@Value
@AllArgsConstructor(staticName = "of")
public class AppnexusReqExt {

    AppnexusReqExtAppnexus appnexus;

    ExtRequestPrebid prebid;
}
