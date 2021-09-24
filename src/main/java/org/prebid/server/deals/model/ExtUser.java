package org.prebid.server.deals.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtUser {

    @JsonProperty("fcapIds")
    List<String> fcapIds;
}
