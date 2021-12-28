package org.prebid.server.bidder.ttx.proto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class TtxExtTtx {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<TtxExtTtxCaller> caller;
}
