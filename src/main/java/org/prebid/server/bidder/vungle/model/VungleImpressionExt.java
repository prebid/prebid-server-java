package org.prebid.server.bidder.vungle.model;

import lombok.Builder;
import lombok.Getter;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.vungle.ExtImpVungle;

@Builder(toBuilder = true)
@Getter
public class VungleImpressionExt {

    ExtImpPrebid prebid;

    ExtImpVungle bidder;

    ExtImpVungle vungle;
}
