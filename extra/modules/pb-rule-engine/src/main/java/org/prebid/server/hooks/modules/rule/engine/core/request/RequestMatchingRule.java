package org.prebid.server.hooks.modules.rule.engine.core.request;

import com.iab.openrtb.request.BidRequest;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.rule.engine.core.rules.Rule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleConfig;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;
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

public class RequestMatchingRule implements Rule<BidRequest, RequestRuleContext> {

    private final Schema<BidRequest, RequestRuleContext> schema;
    private final RuleTree<RuleConfig<BidRequest, RequestRuleContext>> ruleTree;

    private final String modelVersion;
    private final String analyticsKey;

    public RequestMatchingRule(Schema<BidRequest, RequestRuleContext> schema,
                               RuleTree<RuleConfig<BidRequest, RequestRuleContext>> ruleTree,
                               String modelVersion,
                               String analyticsKey) {

        this.schema = Objects.requireNonNull(schema);

        this.ruleTree = Objects.requireNonNull(ruleTree);
        this.modelVersion = StringUtils.defaultString(modelVersion);
        this.analyticsKey = StringUtils.defaultString(analyticsKey);
    }

    @Override
    public RuleResult<BidRequest> process(BidRequest bidRequest, RequestRuleContext context) {
        final List<SchemaFunctionHolder<BidRequest, RequestRuleContext>> schemaFunctions = schema.getFunctions();
        final List<String> matchers = schemaFunctions.stream()
                .map(holder -> holder.getSchemaFunction().extract(
                        SchemaFunctionArguments.of(bidRequest, holder.getConfig(), context)))
                .map(matcher -> StringUtils.defaultIfEmpty(matcher, UNDEFINED_RESULT))
                .toList();

        final LookupResult<RuleConfig<BidRequest, RequestRuleContext>> lookupResult;
        try {
            lookupResult = ruleTree.lookup(matchers);
        } catch (NoMatchingRuleException e) {
            return RuleResult.unaltered(bidRequest);
        }

        final RuleConfig<BidRequest, RequestRuleContext> ruleConfig = lookupResult.getValue();

        final InfrastructureArguments<RequestRuleContext> infrastructureArguments =
                InfrastructureArguments.<RequestRuleContext>builder()
                        .context(context)
                        .schemaFunctionResults(mergeWithSchema(schema, matchers))
                        .schemaFunctionMatches(mergeWithSchema(schema, lookupResult.getMatches()))
                        .ruleFired(ruleConfig.getCondition())
                        .analyticsKey(analyticsKey)
                        .modelVersion(modelVersion)
                        .build();

        return ruleConfig.getActions().stream().reduce(
                RuleResult.unaltered(bidRequest),
                (result, action) -> result.mergeWith(
                        action.getFunction().apply(
                                ResultFunctionArguments.of(
                                        result.getUpdateResult().getValue(),
                                        action.getConfig(),
                                        infrastructureArguments))),
                RuleResult::mergeWith);
    }

    private static Map<String, String> mergeWithSchema(Schema<BidRequest, RequestRuleContext> schema, List<String> values) {
        return IntStream.range(0, values.size())
                .boxed()
                .collect(Collectors.toMap(
                        idx -> schema.getFunctions().get(idx).getName(), values::get, (left, right) -> left));
    }
}
