package org.prebid.server.bidder.thirtythreeacross.proto;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ThirtyThreeAcrossExtTtx {

    List<ThirtyThreeAcrossExtTtxCaller> caller;
}
