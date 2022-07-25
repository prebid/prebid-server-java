package org.prebid.server.bidder.rubicon.proto.request;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class RubiconImpExt {

    RubiconImpExtRp rp;

    List<String> viewabilityvendors;

    Integer maxbids;

    String gpid;

    RubiconImpExtPrebid prebid;
}
