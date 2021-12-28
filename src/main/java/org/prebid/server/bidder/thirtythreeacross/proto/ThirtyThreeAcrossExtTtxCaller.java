package org.prebid.server.bidder.thirtythreeacross.proto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Value;

@Value(staticConstructor = "of")
public class ThirtyThreeAcrossExtTtxCaller {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String name;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String version;
}
