package org.prebid.server.functional.model.config.privacy

import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.ActivityType

import static DataActivity.INVALID
import static LogicalOperation.OR
import static UsNationalPrivacySection.GPC

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

    static ActivityConfig getConfigWithDefaultRestrictRules(List<ActivityType> activities = ActivityType.values()) {
        new ActivityConfig().tap {
            it.activities = activities
            it.restrictIfTrue = LogicalRestrictedRule.generateSingleRestrictedRule(OR, [new InequalityValueRule(GPC, INVALID)])
        }
    }
}
