package org.prebid.server.hooks.modules.rule.engine.core.rules;

import org.apache.commons.collections4.ListUtils;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.InfrastructureArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.RuleAction;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class DefaultActionRule<T, C> implements Rule<T, C> {

    private static final String RULE_NAME = "default";

    private final List<RuleAction<T, C>> actions;

    private final String analyticsKey;
    private final String modelVersion;

    public DefaultActionRule(List<RuleAction<T, C>> actions, String analyticsKey, String modelVersion) {
        this.actions = ListUtils.emptyIfNull(actions);

        this.analyticsKey = Objects.requireNonNull(analyticsKey);
        this.modelVersion = Objects.requireNonNull(modelVersion);
    }

    @Override
    public RuleResult<T> process(T value, C context) {
        return actions.stream().reduce(
                RuleResult.unaltered(value),
                (result, action) ->
                        result.mergeWith(applyAction(action, result.getUpdateResult().getValue(), context)),
                RuleResult::mergeWith);
    }

    private RuleResult<T> applyAction(RuleAction<T, C> action, T value, C context) {
        final ResultFunctionArguments<T, C> arguments = ResultFunctionArguments.of(
                value, action.getConfig(), infrastructureArguments(context));

        return action.getFunction().apply(arguments);
    }

    private InfrastructureArguments<C> infrastructureArguments(C context) {
        return InfrastructureArguments.<C>builder()
                .context(context)
                .schemaFunctionResults(Collections.emptyMap())
                .schemaFunctionMatches(Collections.emptyMap())
                .ruleFired(RULE_NAME)
                .analyticsKey(analyticsKey)
                .modelVersion(modelVersion)
                .build();
    }
}
