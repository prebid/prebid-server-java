package org.prebid.server.activity.infrastructure.creator.rule;

import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.auction.gpp.model.GppContext;

import java.util.Objects;

public abstract class AbstractRuleCreator<T> implements RuleCreator<T> {

    private final Class<T> relatedConfigurationClass;

    protected AbstractRuleCreator(Class<T> relatedConfigurationClass) {
        this.relatedConfigurationClass = Objects.requireNonNull(relatedConfigurationClass);
    }

    @Override
    public Class<T> relatedConfigurationClass() {
        return relatedConfigurationClass;
    }

    @Override
    public Rule from(Object ruleConfiguration, GppContext gppContext) {
        if (!relatedConfigurationClass.isInstance(ruleConfiguration)) {
            throw new AssertionError();
        }

        return fromConfiguration(relatedConfigurationClass.cast(ruleConfiguration), gppContext);
    }

    protected abstract Rule fromConfiguration(T ruleConfiguration, GppContext gppContext);
}
