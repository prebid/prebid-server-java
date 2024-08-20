package org.prebid.server.activity.infrastructure.creator.rule;

import org.prebid.server.activity.infrastructure.creator.ActivityControllerCreationContext;
import org.prebid.server.activity.infrastructure.rule.AndRule;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.settings.model.activity.rule.AccountActivityConditionsRuleConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ConditionsRuleCreator extends AbstractRuleCreator<AccountActivityConditionsRuleConfig> {

    private final ComponentRuleCreator componentRuleCreator;
    private final GeoRuleCreator geoRuleCreator;
    private final GpcRuleCreator gpcRuleCreator;

    public ConditionsRuleCreator(ComponentRuleCreator componentRuleCreator,
                                 GeoRuleCreator geoRuleCreator,
                                 GpcRuleCreator gpcRuleCreator) {

        super(AccountActivityConditionsRuleConfig.class);

        this.componentRuleCreator = Objects.requireNonNull(componentRuleCreator);
        this.geoRuleCreator = Objects.requireNonNull(geoRuleCreator);
        this.gpcRuleCreator = Objects.requireNonNull(gpcRuleCreator);
    }

    @Override
    protected Rule fromConfiguration(AccountActivityConditionsRuleConfig ruleConfiguration,
                                     ActivityControllerCreationContext creationContext) {

        final AccountActivityConditionsRuleConfig.Condition condition = ruleConfiguration.getCondition();
        if (condition == null) {
            return new AndRule(Collections.emptyList());
        }

        final List<Rule> conditions = new ArrayList<>();
        if (condition.getComponentTypes() != null || condition.getComponentNames() != null) {
            conditions.add(componentRuleCreator.fromConfiguration(ruleConfiguration, creationContext));
        }
        if (condition.getSids() != null || condition.getGeoCodes() != null) {
            conditions.add(geoRuleCreator.fromConfiguration(ruleConfiguration, creationContext));
        }
        if (condition.getGpc() != null) {
            conditions.add(gpcRuleCreator.fromConfiguration(ruleConfiguration, creationContext));
        }

        return new AndRule(conditions);
    }
}
