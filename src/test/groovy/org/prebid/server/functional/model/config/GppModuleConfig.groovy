package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString
import org.prebid.server.functional.model.request.GppSectionId

@ToString(includeNames = true, ignoreNulls = true)
class GppModuleConfig {

    List<ActivityConfig> activityConfig
    @JsonProperty("activity_config")
    List<ActivityConfig> activityConfigSnakeCase
    @JsonProperty("activity-config")
    List<ActivityConfig> activityConfigKebabCase
    List<GppSectionId> sids
    Boolean normalizeFlags
    @JsonProperty("normalize_flags")
    Boolean normalizeFlagsSnakeCase
    @JsonProperty("normalize-flags")
    Boolean normalizeFlagsKebabCase
    List<GppSectionId> skipSids
    @JsonProperty("skip_sids")
    List<GppSectionId> skipSidsSnakeCase
    @JsonProperty("skip-sids")
    List<GppSectionId> skipSidsKebabCase
    Boolean allowPersonalDataConsent2
    @JsonProperty("allow_personal_data_consent_2")
    Boolean allowPersonalDataConsent2SnakeCase
    @JsonProperty("allow-personal-data-consent-2")
    Boolean allowPersonalDataConsent2KebabCase

    static GppModuleConfig getDefaultModuleConfig(ActivityConfig activityConfig = ActivityConfig.configWithDefaultRestrictRules,
                                                  List<GppSectionId> sids = [GppSectionId.US_NAT_V1],
                                                  Boolean normalizeFlags = true) {
        new GppModuleConfig().tap {
            it.activityConfig = [activityConfig]
            it.sids = sids
            it.normalizeFlags = normalizeFlags
        }
    }

    static GppModuleConfig getDefaultModuleConfigSnakeCase(ActivityConfig activityConfig = ActivityConfig.configWithDefaultRestrictRules,
                                                           List<GppSectionId> sids = [GppSectionId.US_NAT_V1],
                                                           Boolean normalizeFlags = true) {
        new GppModuleConfig().tap {
            it.activityConfigSnakeCase = [activityConfig]
            it.sids = sids
            it.normalizeFlagsSnakeCase = normalizeFlags
        }
    }

    static GppModuleConfig getDefaultModuleConfigKebabCase(ActivityConfig activityConfig = ActivityConfig.configWithDefaultRestrictRules,
                                                           List<GppSectionId> sids = [GppSectionId.US_NAT_V1],
                                                           Boolean normalizeFlags = true) {
        new GppModuleConfig().tap {
            it.activityConfigKebabCase = [activityConfig]
            it.sids = sids
            it.normalizeFlagsKebabCase = normalizeFlags
        }
    }
}
