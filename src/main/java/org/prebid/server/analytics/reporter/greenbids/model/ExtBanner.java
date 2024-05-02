package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class ExtBanner {

    @JsonProperty("sizes")
    List<List<Integer>> sizes;

    @JsonProperty("pos")
    Integer pos;

    @JsonProperty("name")
    String name;
}
