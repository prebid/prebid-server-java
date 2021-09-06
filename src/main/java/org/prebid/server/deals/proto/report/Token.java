package org.prebid.server.deals.proto.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class Token {

    @JsonProperty("class")
    Integer priorityClass;

    Integer total;

    Long spent;

    @JsonProperty("totalSpent")
    Long totalSpent;
}
