package org.prebid.server.activity.infrastructure.creator.rule;

import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.activity.infrastructure.rule.ComponentRule;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.settings.model.activity.rule.AccountActivityComponentRuleConfig;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ComponentRuleCreator extends AbstractRuleCreator<AccountActivityComponentRuleConfig> {

    public ComponentRuleCreator() {
        super(AccountActivityComponentRuleConfig.class);
    }

    @Override
    protected Rule fromConfiguration(AccountActivityComponentRuleConfig ruleConfiguration, GppContext gppContext) {
        final boolean allow = allowFromConfig(ruleConfiguration.getAllow());
        final AccountActivityComponentRuleConfig.Condition condition = ruleConfiguration.getCondition();

        return new ComponentRule(
                condition != null ? setOf(condition.getComponentTypes()) : null,
                condition != null ? setOf(condition.getComponentNames()) : null,
                allow);
    }

    private static boolean allowFromConfig(Boolean configValue) {
        return configValue != null ? configValue : ActivityInfrastructure.ALLOW_ACTIVITY_BY_DEFAULT;
    }

    private static <V> Set<V> setOf(Collection<V> collection) {
        return collection != null ? new HashSet<>(collection) : null;
    }
}
