package org.prebid.server.hooks.modules.rule.engine.core.rules.request;

import com.iab.openrtb.request.BidRequest;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.rule.engine.core.rules.Rule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleConfig;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.InfrastructureArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.RuleAction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.Schema;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionHolder;
import org.prebid.server.hooks.modules.rule.engine.core.rules.tree.RuleTree;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class RequestMatchingRule implements Rule<BidRequest> {

    private static final String NULL_MATCHER = "null";

    private final Schema<RequestContext> schema;
    private final Set<String> schemaFunctionNames;
    private final RuleTree<RuleConfig<BidRequest>> ruleTree;

    private final String modelVersion;
    private final String analyticsKey;
    private final String datacenter;

    public RequestMatchingRule(Schema<RequestContext> schema,
                               RuleTree<RuleConfig<BidRequest>> ruleTree,
                               String modelVersion,
                               String analyticsKey,
                               String datacenter) {

        this.schema = Objects.requireNonNull(schema);
        this.schemaFunctionNames = schema.getFunctions().stream()
                .map(SchemaFunctionHolder::getName)
                .collect(Collectors.toSet());

        this.ruleTree = Objects.requireNonNull(ruleTree);
        this.modelVersion = StringUtils.defaultString(modelVersion);
        this.analyticsKey = StringUtils.defaultString(analyticsKey);
        this.datacenter = StringUtils.defaultString(datacenter);
    }

    @Override
    public RuleResult<BidRequest> process(BidRequest bidRequest) {
        return SetUtils.intersection(schemaFunctionNames, RequestSpecification.PER_IMP_SCHEMA_FUNCTIONS).isEmpty()
                ? processRule(bidRequest, null)
                : processPerImpRule(bidRequest);
    }

    private RuleResult<BidRequest> processPerImpRule(BidRequest bidRequest) {
        return bidRequest.getImp().stream().reduce(
                RuleResult.unaltered(bidRequest),
                (result, imp) ->
                        result.mergeWith(processRule(result.getUpdateResult().getValue(), imp.getId())),
                RuleResult::mergeWith);
    }

    private RuleResult<BidRequest> processRule(BidRequest bidRequest, String impId) {
        final RequestContext context = RequestContext.of(bidRequest, impId, datacenter);

        final List<SchemaFunctionHolder<RequestContext>> schemaFunctions = schema.getFunctions();
        final List<String> matchers = schemaFunctions.stream()
                .map(holder -> holder.getSchemaFunction().extract(
                        SchemaFunctionArguments.of(context, holder.getArguments())))
                .map(matcher -> StringUtils.defaultIfEmpty(matcher, NULL_MATCHER))
                .toList();

        final Map<String, String> schemaFunctionResults = IntStream.range(0, matchers.size())
                .boxed()
                .collect(Collectors.toMap(
                        idx -> schemaFunctions.get(idx).getName(), matchers::get, (left, right) -> left));

        final RuleConfig<BidRequest> ruleConfig = ruleTree.getValue(matchers);

        return ruleConfig.getActions().stream().reduce(
                RuleResult.unaltered(bidRequest),
                (result, action) -> result.mergeWith(
                        applyAction(
                                action,
                                result.getUpdateResult().getValue(),
                                schemaFunctionResults,
                                ruleConfig.getCondition())),
                RuleResult::mergeWith);
    }

    private RuleResult<BidRequest> applyAction(RuleAction<BidRequest> action,
                                               BidRequest bidRequest,
                                               Map<String, String> schemaFunctionResults,
                                               String condition) {

        final InfrastructureArguments infrastructureArguments = InfrastructureArguments.of(
                schemaFunctionResults, analyticsKey, condition, modelVersion);

        final ResultFunctionArguments<BidRequest> arguments = ResultFunctionArguments.of(
                bidRequest, action.getConfigArguments(), infrastructureArguments);

        return action.getFunction().apply(arguments);
    }
}
