package org.prebid.server.hooks.modules.optable.targeting.model.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;

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

    @JsonProperty("optable-inserter-eids-merge")
    Set<String> optableInserterEidsMerge = Set.of();

    @JsonProperty("optable-inserter-eids-replace")
    Set<String> optableInserterEidsReplace = Set.of();

    @JsonProperty("optable-inserter-eids-ignore")
    Set<String> optableInserterEidsIgnore = Set.of();

    CacheProperties cache = new CacheProperties();
}
