package org.prebid.server.activity.infrastructure.creator.rule;

import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.activity.infrastructure.creator.ActivityControllerCreationContext;
import org.prebid.server.activity.infrastructure.rule.ComponentRule;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.settings.model.activity.rule.AccountActivityComponentRuleConfig;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class ComponentRuleCreator extends AbstractRuleCreator<AccountActivityComponentRuleConfig> {

    public ComponentRuleCreator() {
        super(AccountActivityComponentRuleConfig.class);
    }

    @Override
    protected Rule fromConfiguration(AccountActivityComponentRuleConfig ruleConfiguration,
                                     ActivityControllerCreationContext creationContext) {

        final boolean allow = allowFromConfig(ruleConfiguration.getAllow());
        final AccountActivityComponentRuleConfig.Condition condition = ruleConfiguration.getCondition();

        return new ComponentRule(
                condition != null ? setOf(condition.getComponentTypes()) : null,
                condition != null ? caseInsensitiveSetOf(condition.getComponentNames()) : null,
                allow);
    }

    private static boolean allowFromConfig(Boolean configValue) {
        return configValue != null ? configValue : ActivityInfrastructure.ALLOW_ACTIVITY_BY_DEFAULT;
    }

    private static Set<ComponentType> setOf(Collection<ComponentType> collection) {
        return collection != null ? new HashSet<>(collection) : null;
    }

    private static Set<String> caseInsensitiveSetOf(Collection<String> collection) {
        if (collection == null) {
            return null;
        }

        final Set<String> caseInsensitiveSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        caseInsensitiveSet.addAll(collection);
        return caseInsensitiveSet;
    }
}
