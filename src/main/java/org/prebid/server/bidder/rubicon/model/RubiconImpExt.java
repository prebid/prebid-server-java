package org.prebid.server.bidder.rubicon.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class RubiconImpExt {

    RubiconImpExtRp rp;
}
