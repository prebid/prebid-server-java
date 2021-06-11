package org.prebid.server.bidder.rubicon.proto;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class RubiconImpExt {

    RubiconImpExtRp rp;

    List<String> viewabilityvendors;

    Integer maxbids;
}
