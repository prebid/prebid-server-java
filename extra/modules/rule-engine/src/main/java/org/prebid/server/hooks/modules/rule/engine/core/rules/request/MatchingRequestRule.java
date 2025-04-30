package org.prebid.server.hooks.modules.rule.engine.core.rules.request;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.modules.rule.engine.core.rules.Rule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleConfig;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.RuleAction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.arguments.InfrastructureArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.arguments.ResultFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.Schema;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.tree.RuleTree;
import org.prebid.server.model.UpdateResult;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MatchingRequestRule implements Rule<BidRequest> {

    private final Schema<RequestPayload> schema;
    private final RuleTree<RuleConfig<BidRequest>> ruleTree;

    public MatchingRequestRule(Schema<RequestPayload> schema, RuleTree<RuleConfig<BidRequest>> ruleTree) {
        this.schema = Objects.requireNonNull(schema);
        this.ruleTree = Objects.requireNonNull(ruleTree);
    }

    @Override
    public RuleResult<BidRequest> process(BidRequest bidRequest) {
        RuleResult<BidRequest> result = unalteredResult(bidRequest);
        if (schema.getNames().contains("adUnitCode") || schema.getNames().contains("mediaType")) {
            for (Imp imp : bidRequest.getImp()) {
                final RuleResult<BidRequest> updateResult = processRule(
                        result.getUpdateResult().getValue(), imp.getId());
                result = result.mergeWith(updateResult);
            }
        } else {
            result = processRule(bidRequest, null);
        }

        return result;
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

            for (ResultFunctionArguments arguments : action.getArguments()) {
                final RuleResult<BidRequest> updateResult = action.getFunction()
                        .apply(arguments, infrastructureArguments, result.getUpdateResult().getValue());

                result = result.mergeWith(updateResult);
            }
        }

        return result;
    }

    private static RuleResult<BidRequest> unalteredResult(BidRequest bidRequest) {
        return RuleResult.of(UpdateResult.unaltered(bidRequest), TagsImpl.of(Collections.emptyList()));
    }
}
