package org.prebid.server.deals.proto.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class Token {

    @JsonProperty("class")
    Integer priorityClass;

    Integer total;

    Long spent;

    @JsonProperty("totalSpent")
    Long totalSpent;
}
