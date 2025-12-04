package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString
import org.prebid.server.functional.model.request.GppSectionId

import static org.prebid.server.functional.model.config.GppModuleConfig.ConfigCase.CAMEL_CASE
import static org.prebid.server.functional.model.config.GppModuleConfig.ConfigCase.KEBAB_CASE

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
                                                  Boolean normalizeFlags = true,
                                                  ConfigCase configCase = CAMEL_CASE) {

        new GppModuleConfig().tap {
            it.sids = sids
            if (configCase == CAMEL_CASE) {
                it.activityConfig = [activityConfig]
                it.normalizeFlags = normalizeFlags
            } else if (configCase = KEBAB_CASE) {
                it.activityConfigKebabCase = [activityConfig]
                it.normalizeFlagsKebabCase = normalizeFlags
            } else {
                it.activityConfigSnakeCase = [activityConfig]
                it.normalizeFlagsSnakeCase = normalizeFlags
            }
        }
    }

    enum ConfigCase {
        CAMEL_CASE, KEBAB_CASE, SNAKE_CASE
    }
}
