package org.prebid.server.hooks.modules.optable.targeting.model.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public final class OptableTargetingProperties {

    @JsonProperty("api-endpoint")
    String apiEndpoint;

    @JsonProperty("api-key")
    String apiKey;

    String tenant;

    String origin;

    @JsonProperty("ppid-mapping")
    Map<String, String> ppidMapping;

    @JsonProperty("adserver-targeting")
    Boolean adserverTargeting = true;

    Long timeout;

    @JsonProperty("id-prefix-order")
    String idPrefixOrder;

    @JsonProperty("no-skip")
    List<String> noSkip;

    @JsonProperty("optable-inserter-eids-merge")
    List<String> optableInserterEidsMerge = List.of();

    @JsonProperty("optable-inserter-eids-replace")
    List<String> optableInserterEidsReplace = List.of();

    CacheProperties cache = new CacheProperties();
}
