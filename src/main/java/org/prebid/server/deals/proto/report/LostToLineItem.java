package org.prebid.server.deals.proto.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class LostToLineItem {

    @JsonProperty("lineItemSource")
    String lineItemSource;

    @JsonProperty("lineItemId")
    String lineItemId;

    Long count;
}
