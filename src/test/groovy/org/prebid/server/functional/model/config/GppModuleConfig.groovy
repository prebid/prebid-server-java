package org.prebid.server.functional.model.config

import groovy.transform.ToString
import org.prebid.server.functional.model.request.GppSectionId

@ToString(includeNames = true, ignoreNulls = true)
class GppModuleConfig {

    List<ActivityConfig> activityConfig
    List<GppSectionId> sids
    Boolean normalizeFlags
    List<GppSectionId> skipSids

    static GppModuleConfig getDefaultModuleConfig(ActivityConfig activityConfig = ActivityConfig.configWithDefaultRestrictRules,
                                                  List<GppSectionId> sids = [GppSectionId.US_NAT_V1],
                                                  Boolean normalizeFlags = true) {
        new GppModuleConfig().tap {
            it.activityConfig = [activityConfig]
            it.sids = sids
            it.normalizeFlags = normalizeFlags
        }
    }
}
