package org.prebid.server.activity.infrastructure.creator.rule;

import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.creator.ActivityControllerCreationContext;
import org.prebid.server.activity.infrastructure.rule.ComponentRule;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.settings.model.activity.rule.AccountActivityConditionsRuleConfig;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class ComponentRuleCreator extends AbstractRuleCreator<AccountActivityConditionsRuleConfig> {

    public ComponentRuleCreator() {
        super(AccountActivityConditionsRuleConfig.class);
    }

    @Override
    protected Rule fromConfiguration(AccountActivityConditionsRuleConfig ruleConfiguration,
                                     ActivityControllerCreationContext creationContext) {

        final AccountActivityConditionsRuleConfig.Condition condition = ruleConfiguration.getCondition();

        return new ComponentRule(
                setOf(condition.getComponentTypes()),
                caseInsensitiveSetOf(condition.getComponentNames()),
                allowFromConfig(ruleConfiguration.getAllow()));
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
