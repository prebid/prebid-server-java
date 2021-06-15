package org.prebid.server.bidder.rubicon.proto.request;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class RubiconVideoExt {

    Integer skip;

    Integer skipdelay;

    RubiconVideoExtRp rp;

    String videotype;
}
