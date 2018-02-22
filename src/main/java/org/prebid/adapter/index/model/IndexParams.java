package org.prebid.adapter.index.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class IndexParams {

    @JsonProperty("siteID")
    Integer siteId;
}
