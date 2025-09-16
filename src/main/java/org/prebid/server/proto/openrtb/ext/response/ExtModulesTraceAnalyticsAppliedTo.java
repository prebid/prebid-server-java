package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class ExtModulesTraceAnalyticsAppliedTo {

    @JsonProperty("impids")
    List<String> impIds;

    List<String> bidders;

    Boolean request;

    Boolean response;

    @JsonProperty("bidids")
    List<String> bidIds;
}
