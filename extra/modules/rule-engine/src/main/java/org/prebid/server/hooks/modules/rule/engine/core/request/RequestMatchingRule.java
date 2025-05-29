package org.prebid.server.hooks.modules.rule.engine.core.request;

import com.iab.openrtb.request.BidRequest;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
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

public class RequestMatchingRule implements Rule<BidRequest, AuctionContext> {

    private static final String NULL_MATCHER = "null";

    private final Schema<RequestContext> schema;
    private final Set<String> schemaFunctionNames;
    private final RuleTree<RuleConfig<BidRequest, AuctionContext>> ruleTree;

    private final String modelVersion;
    private final String analyticsKey;
    private final String datacenter;

    public RequestMatchingRule(Schema<RequestContext> schema,
                               RuleTree<RuleConfig<BidRequest, AuctionContext>> ruleTree,
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
    public RuleResult<BidRequest> process(BidRequest bidRequest, AuctionContext context) {
        return SetUtils.intersection(schemaFunctionNames, RequestSpecification.PER_IMP_SCHEMA_FUNCTIONS).isEmpty()
                ? processRule(bidRequest, null, context)
                : processPerImpRule(bidRequest, context);
    }

    private RuleResult<BidRequest> processPerImpRule(BidRequest bidRequest, AuctionContext context) {
        return bidRequest.getImp().stream().reduce(
                RuleResult.unaltered(bidRequest),
                (result, imp) ->
                        result.mergeWith(processRule(result.getUpdateResult().getValue(), imp.getId(), context)),
                RuleResult::mergeWith);
    }

    private RuleResult<BidRequest> processRule(BidRequest bidRequest, String impId, AuctionContext auctionContext) {
        final RequestContext requestContext = RequestContext.of(bidRequest, impId, datacenter);

        final List<SchemaFunctionHolder<RequestContext>> schemaFunctions = schema.getFunctions();
        final List<String> matchers = schemaFunctions.stream()
                .map(holder -> holder.getSchemaFunction().extract(
                        SchemaFunctionArguments.of(requestContext, holder.getConfig())))
                .map(matcher -> StringUtils.defaultIfEmpty(matcher, NULL_MATCHER))
                .toList();

        final Map<String, String> schemaFunctionResults = IntStream.range(0, matchers.size())
                .boxed()
                .collect(Collectors.toMap(
                        idx -> schemaFunctions.get(idx).getName(), matchers::get, (left, right) -> left));

        final RuleConfig<BidRequest, AuctionContext> ruleConfig = ruleTree.getValue(matchers);

        return ruleConfig.getActions().stream().reduce(
                RuleResult.unaltered(bidRequest),
                (result, action) -> result.mergeWith(
                        applyAction(
                                action,
                                result.getUpdateResult().getValue(),
                                auctionContext,
                                schemaFunctionResults,
                                ruleConfig.getCondition())),
                RuleResult::mergeWith);
    }

    private RuleResult<BidRequest> applyAction(RuleAction<BidRequest, AuctionContext> action,
                                               BidRequest bidRequest,
                                               AuctionContext context,
                                               Map<String, String> schemaFunctionResults,
                                               String condition) {

        final InfrastructureArguments<AuctionContext> infrastructureArguments = InfrastructureArguments.of(
                context, schemaFunctionResults, analyticsKey, condition, modelVersion);

        final ResultFunctionArguments<BidRequest, AuctionContext> arguments = ResultFunctionArguments.of(
                bidRequest, action.getConfig(), infrastructureArguments);

        return action.getFunction().apply(arguments);
    }
}
