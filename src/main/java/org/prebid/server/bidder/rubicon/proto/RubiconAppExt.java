package org.prebid.server.bidder.rubicon.proto;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class RubiconAppExt {

    RubiconSiteExtRp rp;
}
