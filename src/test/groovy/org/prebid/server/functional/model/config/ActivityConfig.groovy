package org.prebid.server.functional.model.config

import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.ActivityType

import static org.prebid.server.functional.model.config.DataActivity.CONSENT
import static org.prebid.server.functional.model.config.DataActivity.NOT_APPLICABLE
import static org.prebid.server.functional.model.config.DataActivity.NO_CONSENT
import static org.prebid.server.functional.model.config.LogicalRestrictedRule.LogicalOperation.OR
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.CHILD_CONSENTS_BELOW_13
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.CHILD_CONSENTS_FROM_13_TO_16
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.SHARING_OPT_OUT_NOTICE
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.GPC
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.MSPA_SERVICE_PROVIDER_MODE
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.PERSONAL_DATA_CONSENTS
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.SALE_OPT_OUT
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.SALE_OPT_OUT_NOTICE
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.SHARING_NOTICE
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.SHARING_OPT_OUT
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.TARGETED_ADVERTISING_OPT_OUT
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.TARGETED_ADVERTISING_OPT_OUT_NOTICE

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

        def personalDataAndSharingRestriction = LogicalRestrictedRule.generateSolidRestriction(OR, [new ValueRestrictedRule(true, MSPA_SERVICE_PROVIDER_MODE, CONSENT),
                                                                                                    new ValueRestrictedRule(true, GPC, CONSENT),
                                                                                                    new ValueRestrictedRule(true, SALE_OPT_OUT, CONSENT),
                                                                                                    new ValueRestrictedRule(true, SALE_OPT_OUT_NOTICE, CONSENT),
                                                                                                    new ValueRestrictedRule(true, SHARING_NOTICE, NO_CONSENT),
                                                                                                    new ValueRestrictedRule(true, SHARING_OPT_OUT_NOTICE, NO_CONSENT),
                                                                                                    new ValueRestrictedRule(true, SHARING_OPT_OUT, CONSENT),
                                                                                                    new ValueRestrictedRule(true, TARGETED_ADVERTISING_OPT_OUT_NOTICE, NO_CONSENT),
                                                                                                    new ValueRestrictedRule(true, TARGETED_ADVERTISING_OPT_OUT, CONSENT),
                                                                                                    new ValueRestrictedRule(true, CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE),
                                                                                                    new ValueRestrictedRule(true, CHILD_CONSENTS_BELOW_13, CONSENT)])

        def personalDataRestriction = LogicalRestrictedRule.rootLogicalRestricted
                .includeSubRestriction(OR, new ValueRestrictedRule(true, PERSONAL_DATA_CONSENTS, NO_CONSENT))
                .includeSubRestriction(OR, personalDataAndSharingRestriction)

        new ActivityConfig().tap {
            it.activities = activities
            it.restrictIfTrue = personalDataRestriction
        }
    }
}
