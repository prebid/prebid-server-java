package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.request.Format;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class ExtBanner {

    @JsonProperty("sizes")
    List<Format> sizes;

    @JsonProperty("pos")
    Integer pos;

    @JsonProperty("name")
    String name;
}
