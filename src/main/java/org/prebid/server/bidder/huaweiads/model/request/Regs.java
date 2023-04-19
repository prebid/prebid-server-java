package org.prebid.server.bidder.huaweiads.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Value;

@Value(staticConstructor = "of")
public class Regs {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Integer coppa;

}
