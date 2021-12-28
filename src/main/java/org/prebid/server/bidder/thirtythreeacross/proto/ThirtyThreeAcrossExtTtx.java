package org.prebid.server.bidder.thirtythreeacross.proto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ThirtyThreeAcrossExtTtx {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<ThirtyThreeAcrossExtTtxCaller> caller;
}
