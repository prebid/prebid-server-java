package org.prebid.server.hooks.modules.rule.engine.core.rules;

import lombok.EqualsAndHashCode;
import org.apache.commons.collections4.ListUtils;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.InfrastructureArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionHolder;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@EqualsAndHashCode
public class DefaultActionRule<T, C> implements Rule<T, C> {

    private static final String RULE_NAME = "default";

    private final List<ResultFunctionHolder<T, C>> actions;

    private final String analyticsKey;
    private final String modelVersion;

    public DefaultActionRule(List<ResultFunctionHolder<T, C>> actions, String analyticsKey, String modelVersion) {
        this.actions = ListUtils.emptyIfNull(actions);

        this.analyticsKey = Objects.requireNonNull(analyticsKey);
        this.modelVersion = Objects.requireNonNull(modelVersion);
    }

    @Override
    public RuleResult<T> process(T value, C context) {
        RuleResult<T> result = RuleResult.noAction(value);

        for (ResultFunctionHolder<T, C> action : actions) {
            result = result.mergeWith(applyAction(action, result.getValue(), context));

            if (result.isReject()) {
                return result;
            }
        }

        return result;
    }

    private RuleResult<T> applyAction(ResultFunctionHolder<T, C> action, T value, C context) {
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
