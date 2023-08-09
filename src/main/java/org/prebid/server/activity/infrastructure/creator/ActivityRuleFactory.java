package org.prebid.server.activity.infrastructure.creator;

import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.activity.infrastructure.creator.rule.RuleCreator;
import org.prebid.server.activity.infrastructure.rule.Rule;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ActivityRuleFactory {

    private final Map<Class<?>, RuleCreator<?>> ruleCreators;

    public ActivityRuleFactory(List<RuleCreator<?>> ruleCreators) {
        this.ruleCreators = CollectionUtils.emptyIfNull(ruleCreators).stream()
                .collect(Collectors.toMap(
                        RuleCreator::relatedConfigurationClass,
                        Function.identity()));
    }

    public Rule from(Object ruleConfiguration, ActivityControllerCreationContext activityControllerCreationContext) {
        final Class<?> ruleConfigurationClass = ruleConfiguration.getClass();
        final RuleCreator<?> ruleCreator = ruleCreators.get(ruleConfigurationClass);
        if (ruleCreator == null) {
            throw new IllegalStateException("Rule creator for %s not found.".formatted(ruleConfigurationClass));
        }

        return ruleCreator.from(ruleConfiguration, activityControllerCreationContext);
    }
}
