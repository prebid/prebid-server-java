package org.prebid.server.hooks.modules.rule.engine.core.rules;

import org.apache.commons.collections4.ListUtils;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.InfrastructureArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.RuleAction;

import java.util.Collections;
import java.util.List;

public class DefaultActionRule<T> implements Rule<T> {

    private static final String RULE_NAME = "default";

    private final List<RuleAction<T>> actions;

    private final InfrastructureArguments infrastructureArguments;

    public DefaultActionRule(List<RuleAction<T>> actions, String analyticsKey, String modelVersion) {
        this.actions = ListUtils.emptyIfNull(actions);

        infrastructureArguments = InfrastructureArguments.of(
                Collections.emptyMap(), analyticsKey, RULE_NAME, modelVersion);
    }

    @Override
    public RuleResult<T> process(T value) {
        return actions.stream().reduce(
                RuleResult.unaltered(value),
                (result, action) -> result.mergeWith(applyAction(action, result.getUpdateResult().getValue())),
                RuleResult::mergeWith);
    }

    private RuleResult<T> applyAction(RuleAction<T> action, T value) {
        final ResultFunctionArguments<T> arguments = ResultFunctionArguments.of(
                value, action.getConfigArguments(), infrastructureArguments);

        return action.getFunction().apply(arguments);
    }
}
