package org.prebid.server.bidder.thirtythreeacross.proto;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class ThirtyThreeAcrossReqExtTtx {

    List<ThirtyThreeAcrossCaller> caller;
}
