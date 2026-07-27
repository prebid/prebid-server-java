package org.prebid.server.functional.model.config

import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.ActivityType

import static org.prebid.server.functional.model.config.LogicalRestrictedRule.LogicalOperation.OR
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.GPC
import static org.prebid.server.functional.model.privacy.gpp.GppDataActivity.INVALID

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
            it.restrictIfTrue = LogicalRestrictedRule.generateSingleRestrictedRule(OR, [new  InequalityValueRule(GPC, INVALID)])
        }
    }
}
