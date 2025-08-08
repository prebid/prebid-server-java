package org.prebid.server.hooks.modules.rule.engine.core.rules;

import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.rule.engine.core.rules.exception.NoMatchingRuleException;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.InfrastructureArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.Schema;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionHolder;
import org.prebid.server.hooks.modules.rule.engine.core.rules.tree.LookupResult;
import org.prebid.server.hooks.modules.rule.engine.core.rules.tree.RuleTree;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction.UNDEFINED_RESULT;

public class ConditionMatchingRule<T, C> implements Rule<T, C> {

    private final Schema<T, C> schema;
    private final RuleTree<RuleConfig<T, C>> ruleTree;

    private final String modelVersion;
    private final String analyticsKey;

    public ConditionMatchingRule(Schema<T, C> schema,
                                 RuleTree<RuleConfig<T, C>> ruleTree,
                                 String modelVersion,
                                 String analyticsKey) {

        this.schema = Objects.requireNonNull(schema);

        this.ruleTree = Objects.requireNonNull(ruleTree);
        this.modelVersion = StringUtils.defaultString(modelVersion);
        this.analyticsKey = StringUtils.defaultString(analyticsKey);
    }

    @Override
    public RuleResult<T> process(T value, C context) {
        final List<SchemaFunctionHolder<T, C>> schemaFunctions = schema.getFunctions();
        final List<String> matchers = schemaFunctions.stream()
                .map(holder -> holder.getSchemaFunction().extract(
                        SchemaFunctionArguments.of(value, holder.getConfig(), context)))
                .map(matcher -> StringUtils.defaultIfEmpty(matcher, UNDEFINED_RESULT))
                .toList();

        final LookupResult<RuleConfig<T, C>> lookupResult;
        try {
            lookupResult = ruleTree.lookup(matchers);
        } catch (NoMatchingRuleException e) {
            return RuleResult.unaltered(value);
        }

        final RuleConfig<T, C> ruleConfig = lookupResult.getValue();

        final InfrastructureArguments<C> infrastructureArguments =
                InfrastructureArguments.<C>builder()
                        .context(context)
                        .schemaFunctionResults(mergeWithSchema(schema, matchers))
                        .schemaFunctionMatches(mergeWithSchema(schema, lookupResult.getMatches()))
                        .ruleFired(ruleConfig.getCondition())
                        .analyticsKey(analyticsKey)
                        .modelVersion(modelVersion)
                        .build();

        return ruleConfig.getActions().stream().reduce(
                RuleResult.unaltered(value),
                (result, action) -> result.mergeWith(
                        action.getFunction().apply(
                                ResultFunctionArguments.of(
                                        result.getUpdateResult().getValue(),
                                        action.getConfig(),
                                        infrastructureArguments))),
                RuleResult::mergeWith);
    }

    private Map<String, String> mergeWithSchema(Schema<T, C> schema, List<String> values) {
        return IntStream.range(0, values.size())
                .boxed()
                .collect(Collectors.toMap(
                        idx -> schema.getFunctions().get(idx).getName(), values::get, (left, right) -> left));
    }
}
