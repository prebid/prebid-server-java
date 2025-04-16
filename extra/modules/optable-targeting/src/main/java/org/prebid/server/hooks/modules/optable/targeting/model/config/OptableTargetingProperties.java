package org.prebid.server.hooks.modules.optable.targeting.model.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.prebid.server.hooks.modules.optable.targeting.v1.OptableTargetingModule;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "hooks.modules." + OptableTargetingModule.CODE)
@Data
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

    public OptableTargetingProperties() {
    }

    public OptableTargetingProperties(String apiEndpoint, String apiKey, Map<String, String> ppidMapping,
                                      Boolean adserverTargeting, Long timeout, String idPrefixOrder,
                                      CacheProperties cache) {

        this.apiEndpoint = apiEndpoint;
        this.apiKey = apiKey;
        this.ppidMapping = ppidMapping;
        this.adserverTargeting = adserverTargeting;
        this.timeout = timeout;
        this.idPrefixOrder = idPrefixOrder;
        this.cache = cache;
    }

    CacheProperties cache = new CacheProperties(false, 86400);
}
