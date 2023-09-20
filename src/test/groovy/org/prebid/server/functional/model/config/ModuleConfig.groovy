package org.prebid.server.functional.model.config

import groovy.transform.ToString
import org.prebid.server.functional.model.request.GppSectionId

@ToString(includeNames = true, ignoreNulls = true)
class ModuleConfig {

    List<ActivityConfig> activityConfig
    List<GppSectionId> sids
    Boolean normalizeFlags

    static ModuleConfig getDefaultModuleConfig(ActivityConfig activityConfig = ActivityConfig.configWithDefaultRestrictRules,
                                               List<GppSectionId> sids = [GppSectionId.USP_NAT_V1],
                                               Boolean normalizeFlags = true) {
        new ModuleConfig().tap {
            it.activityConfig = [activityConfig]
            it.sids = sids
            it.normalizeFlags = normalizeFlags
        }
    }
}
