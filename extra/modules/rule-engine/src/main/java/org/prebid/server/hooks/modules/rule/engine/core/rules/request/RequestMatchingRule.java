package org.prebid.server.hooks.modules.rule.engine.core.rules.request;

import com.iab.openrtb.request.BidRequest;
import org.apache.commons.collections4.SetUtils;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.modules.rule.engine.core.rules.Rule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleConfig;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.InfrastructureArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.RuleAction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.Schema;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.tree.RuleTree;
import org.prebid.server.model.UpdateResult;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RequestMatchingRule implements Rule<BidRequest> {

    private final Schema<RequestPayload> schema;
    private final RuleTree<RuleConfig<BidRequest>> ruleTree;

    public RequestMatchingRule(Schema<RequestPayload> schema, RuleTree<RuleConfig<BidRequest>> ruleTree) {
        this.schema = Objects.requireNonNull(schema);
        this.ruleTree = Objects.requireNonNull(ruleTree);
    }

    @Override
    public RuleResult<BidRequest> process(BidRequest bidRequest) {
        return SetUtils.intersection(schema.getNames(), RequestSchema.PER_IMP_SCHEMA_FUNCTIONS).isEmpty()
                ? processRule(bidRequest, null)
                : processPerImpRule(bidRequest);
    }

    private RuleResult<BidRequest> processPerImpRule(BidRequest bidRequest) {
        return bidRequest.getImp().stream().reduce(
                unalteredResult(bidRequest),
                (result, imp) ->
                        result.mergeWith(processRule(result.getUpdateResult().getValue(), imp.getId())),
                RuleResult::mergeWith);
    }

    private RuleResult<BidRequest> processRule(BidRequest bidRequest, String impId) {
        final RequestPayload payload = RequestPayload.of(bidRequest, impId);
        final List<String> matchers = schema.getFunctions().stream()
                .map(holder -> holder.getSchemaFunction()
                        .extract(SchemaFunctionArguments.of(payload, holder.getArguments())))
                .toList();

        final RuleConfig<BidRequest> ruleConfig = ruleTree.getValue(matchers);
        RuleResult<BidRequest> result = unalteredResult(bidRequest);

        for (RuleAction<BidRequest> action : ruleConfig.getActions()) {
            final InfrastructureArguments infrastructureArguments = InfrastructureArguments.of(
                    null,
                    "analyticsKey",
                    ruleConfig.getCondition(),
                    "modelVersion");
            final ResultFunctionArguments<BidRequest> arguments = ResultFunctionArguments.of(
                    result.getUpdateResult().getValue(), action.getConfigArguments(), infrastructureArguments);

            result = result.mergeWith(action.getFunction().apply(arguments));
        }

        return result;
    }

    private static RuleResult<BidRequest> unalteredResult(BidRequest bidRequest) {
        return RuleResult.of(UpdateResult.unaltered(bidRequest), TagsImpl.of(Collections.emptyList()));
    }
}
