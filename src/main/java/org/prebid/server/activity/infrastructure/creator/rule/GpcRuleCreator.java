package org.prebid.server.activity.infrastructure.creator.rule;

import org.prebid.server.activity.infrastructure.creator.ActivityControllerCreationContext;
import org.prebid.server.activity.infrastructure.rule.GpcRule;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.settings.model.activity.rule.AccountActivityConditionsRuleConfig;

public class GpcRuleCreator extends AbstractRuleCreator<AccountActivityConditionsRuleConfig> {

    public GpcRuleCreator() {
        super(AccountActivityConditionsRuleConfig.class);
    }

    @Override
    protected Rule fromConfiguration(AccountActivityConditionsRuleConfig ruleConfiguration,
                                     ActivityControllerCreationContext creationContext) {

        return new GpcRule(ruleConfiguration.getCondition().getGpc(), allowFromConfig(ruleConfiguration.getAllow()));
    }
}
