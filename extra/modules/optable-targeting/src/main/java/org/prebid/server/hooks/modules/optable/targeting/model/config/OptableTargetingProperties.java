package org.prebid.server.hooks.modules.optable.targeting.model.config;

import lombok.Data;
import org.prebid.server.hooks.modules.optable.targeting.v1.OptableTargetingModule;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "hooks.modules." + OptableTargetingModule.CODE)
@Data
public final class OptableTargetingProperties {

    String apiEndpoint;

    String apiKey;

    Map<String, String> ppidMapping;

    Boolean adserverTargeting = true;

    Long timeout;

    String idPrefixOrder;
}
