package org.prebid.server.functional.tests.privacy.activity

import org.prebid.server.functional.model.request.activitie.Activity
import org.prebid.server.functional.model.request.activitie.ActivityRule
import org.prebid.server.functional.model.request.activitie.AllowActivities
import org.prebid.server.functional.model.request.activitie.Condition
import org.prebid.server.functional.tests.BaseSpec

abstract class ActivityBaseSpec extends BaseSpec {


    protected static AllowActivities generateDefaultFetchBidActivities(List<Condition> conditions,
                                                                       boolean isAllowed = true) {
        AllowActivities.defaultAllowActivities.tap {
            fetchBid = Activity.defaultActivityRule.tap {
                rules = generateActivityRules(conditions, isAllowed)
            }
        }
    }

    protected static AllowActivities generateDefaultEnrichUfpdActivities(List<Condition> conditions,
                                                                       boolean isAllowed = true) {
        AllowActivities.defaultAllowActivities.tap {
            enrichUfpd = Activity.defaultActivityRule.tap {
                rules = generateActivityRules(conditions, isAllowed)
            }
        }
    }

    protected static AllowActivities generateDefaultSyncUserActivities(List<Condition> conditions,
                                                                       boolean isAllowed = true) {
        AllowActivities.defaultAllowActivities.tap {
            syncUser = Activity.defaultActivityRule.tap {
                rules = generateActivityRules(conditions, isAllowed)
            }
        }
    }

    private static List<ActivityRule> generateActivityRules(List<Condition> conditions, boolean isAllowed) {
        conditions.collect { singleCondition ->
            ActivityRule.defaultActivityRule.tap {
                allow = isAllowed
                condition = singleCondition
            }
        }
    }


}
