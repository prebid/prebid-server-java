package org.prebid.server.hooks.modules.rule.engine.core.request;

import com.iab.openrtb.request.BidRequest;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction.UNDEFINED_RESULT;

public class RequestMatchingRule implements Rule<BidRequest, AuctionContext> {

    private static final String GLOBAL_IMP_ID = "*";

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
                ? processRule(bidRequest, GLOBAL_IMP_ID, context)
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
                .map(matcher -> StringUtils.defaultIfEmpty(matcher, UNDEFINED_RESULT))
                .toList();

        final LookupResult<RuleConfig<BidRequest, AuctionContext>> lookupResult;
        try {
            lookupResult = ruleTree.lookup(matchers);
        } catch (NoMatchingRuleException e) {
            return RuleResult.unaltered(bidRequest);
        }

        final RuleConfig<BidRequest, AuctionContext> ruleConfig = lookupResult.getValue();

        final InfrastructureArguments<AuctionContext> infrastructureArguments =
                InfrastructureArguments.<AuctionContext>builder()
                        .context(auctionContext)
                        .impId(impId)
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
                                ResultFunctionArguments.of(bidRequest, action.getConfig(), infrastructureArguments))),
                RuleResult::mergeWith);
    }

    private static Map<String, String> mergeWithSchema(Schema<RequestContext> schema, List<String> values) {
        return IntStream.range(0, values.size())
                .boxed()
                .collect(Collectors.toMap(
                        idx -> schema.getFunctions().get(idx).getName(), values::get, (left, right) -> left));
    }
}
