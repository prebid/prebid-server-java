package org.prebid.server.bidder.magnite.proto.request;

import lombok.Value;

@Value(staticConstructor = "of")
public class MagniteVideoExt {

    Integer skip;

    Integer skipdelay;

    MagniteVideoExtRp rp;

    String videotype;
}
