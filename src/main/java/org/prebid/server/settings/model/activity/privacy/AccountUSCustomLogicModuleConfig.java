package org.prebid.server.settings.model.activity.privacy;

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

    Config config;

    @Override
    public PrivacyModuleQualifier getCode() {
        return PrivacyModuleQualifier.US_CUSTOM_LOGIC;
    }

    @Value(staticConstructor = "of")
    public static class Config {

        Set<Integer> sids;

        @JsonProperty("normalizeFlags")
        Boolean normalizeSections;

        @JsonProperty("activityConfig")
        List<ActivityConfig> activitiesConfigs;
    }

    @Value(staticConstructor = "of")
    public static class ActivityConfig {

        Set<Activity> activities;

        @JsonProperty("restrictIfTrue")
        ObjectNode jsonLogicNode;
    }
}
