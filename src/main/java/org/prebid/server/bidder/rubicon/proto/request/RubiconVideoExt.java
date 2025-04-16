package org.prebid.server.bidder.rubicon.proto.request;

import lombok.Value;

@Value(staticConstructor = "of")
public class RubiconVideoExt {

    Integer skip;

    Integer skipdelay;

    RubiconVideoExtRp rp;

    String videotype;
}
