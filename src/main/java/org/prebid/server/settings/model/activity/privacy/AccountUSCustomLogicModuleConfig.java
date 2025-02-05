package org.prebid.server.settings.model.activity.privacy;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModuleQualifier;

import java.util.List;
import java.util.Set;

@Value(staticConstructor = "of")
public class AccountUSCustomLogicModuleConfig implements AccountPrivacyModuleConfig {

    @Accessors(fluent = true)
    Boolean enabled;

    int skipRate;

    Config config;

    @Override
    public PrivacyModuleQualifier getCode() {
        return PrivacyModuleQualifier.US_CUSTOM_LOGIC;
    }

    @Value(staticConstructor = "of")
    public static class Config {

        Set<Integer> sids;

        @JsonProperty("normalize_flags")
        @JsonAlias({"normalizeFlags", "normalize-flags"})
        Boolean normalizeSections;

        @JsonProperty("activity_config")
        @JsonAlias({"activityConfig", "activity-config"})
        List<ActivityConfig> activitiesConfigs;
    }

    @Value(staticConstructor = "of")
    public static class ActivityConfig {

        Set<Activity> activities;

        @JsonProperty("restrict_if_true")
        @JsonAlias({"restrictIfTrue", "restrict-if-true"})
        ObjectNode jsonLogicNode;
    }
}
