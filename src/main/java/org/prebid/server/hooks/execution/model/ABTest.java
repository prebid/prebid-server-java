package org.prebid.server.hooks.execution.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.Set;

@Builder
@Value
public class ABTest {

    boolean enabled;

    @JsonProperty("module-code")
    @JsonAlias("module_code")
    String moduleCode;

    Set<String> accounts;

    @JsonProperty("percent-active")
    @JsonAlias("percent_active")
    Integer percent;

    @JsonProperty("log-analytics-tag")
    @JsonAlias("log_analytics_tag")
    Boolean logAnalyticsTag;
}
