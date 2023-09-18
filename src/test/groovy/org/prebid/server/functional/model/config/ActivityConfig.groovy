package org.prebid.server.functional.model.config

import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.ActivityType

@ToString(includeNames = true, ignoreNulls = true)
class ActivityConfig {

    List<ActivityType> activities
    LogicalRestrictedRule restrictIfTrue

    ActivityConfig() {
    }

    ActivityConfig(List<ActivityType> activities, LogicalRestrictedRule restrictIfTrue) {
        this.activities = activities
        this.restrictIfTrue = restrictIfTrue
    }

    static ActivityConfig getConfigWithDefaultUsNatDisallowLogic(List<ActivityType> activities = ActivityType.values()) {
        new ActivityConfig().tap {
            it.activities = activities
            it.restrictIfTrue = LogicalRestrictedRule.rootLogicalRestricted
        }
    }
}
