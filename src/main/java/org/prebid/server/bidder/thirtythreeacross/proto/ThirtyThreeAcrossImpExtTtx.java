package org.prebid.server.bidder.thirtythreeacross.proto;

import lombok.Value;

@Value(staticConstructor = "of")
public class ThirtyThreeAcrossImpExtTtx {

    String prod;

    String zoneid;
}
