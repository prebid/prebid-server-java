package org.prebid.server.functional.model.config

import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.ActivityType

import static org.prebid.server.functional.model.config.LogicalRestrictedRule.LogicalOperation.OR
import static org.prebid.server.functional.model.config.LogicalRestrictedRule.LogicalOperation.AND

@ToString(includeNames = true, ignoreNulls = true)
class ActivityConfig {

    List<ActivityType> activities
    LogicalRestrictedRule restrictIfTrue

    static ActivityConfig getConfigWithDefaultUsNatDisallowLogic(List<ActivityType> activities = ActivityType.values()) {
        def personalDataAndSharingRestriction = LogicalRestrictedRule.rootLogicalRestricted
                .includeSubRestriction(OR, new ValueRestrictedRule(true, UsNationalPrivacySection.SALE_OPT_OUT, 1))
                .includeSubRestriction(OR, new ValueRestrictedRule(true, UsNationalPrivacySection.SALE_OPT_OUT_NOTICE, 2))
                .includeSubRestriction(OR, new ValueRestrictedRule(true, UsNationalPrivacySection.SHARING_NOTICE, 2))
                .includeSubRestriction(OR, new ValueRestrictedRule(true, UsNationalPrivacySection.SHARING_OPT_OUT, 1))
                .includeSubRestriction(OR, new ValueRestrictedRule(true, UsNationalPrivacySection.TARGETED_ADVERTISING_OPT_OUT_NOTICE, 2))
                .includeSubRestriction(OR, new ValueRestrictedRule(true, UsNationalPrivacySection.TARGETED_ADVERTISING_OPT_OUT, 1))
                .includeSubRestriction(OR, new ValueRestrictedRule(false, UsNationalPrivacySection.KNOWN_CHILD_SENSITIVE_DATA_CONSENTS, 0))
                .includeSubRestriction(OR, new ValueRestrictedRule(true, UsNationalPrivacySection.PERSONAL_DATA_CONSENTS, 1))

        def serviceProviderRestriction = LogicalRestrictedRule.rootLogicalRestricted
                .includeSubRestriction(AND, new ValueRestrictedRule(true, UsNationalPrivacySection.MSPA_SERVICE_PROVIDER_MODE, 2))
                .includeSubRestriction(AND, personalDataAndSharingRestriction)

        def usNational = LogicalRestrictedRule.rootLogicalRestricted
                .includeSubRestriction(OR, new ValueRestrictedRule(true, UsNationalPrivacySection.MSPA_SERVICE_PROVIDER_MODE, 1))
                .includeSubRestriction(OR, new ValueRestrictedRule(true, UsNationalPrivacySection.GPC, 1))
                .includeSubRestriction(OR, serviceProviderRestriction)

        new ActivityConfig().tap {
            it.activities = activities
            it.restrictIfTrue = usNational
        }
    }
}
