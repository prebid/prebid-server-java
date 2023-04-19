package org.prebid.server.bidder.huaweiads.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Value;

@Value(staticConstructor = "of")
public class Geo {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Float lon;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Float lat;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Integer accuracy;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Integer lastfix;

}
