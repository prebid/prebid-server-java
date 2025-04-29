package org.prebid.server.hooks.modules.rule.engine.core.rules.request;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.collections4.ListUtils;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;
import org.prebid.server.hooks.modules.rule.engine.core.rules.tree.RuleTree;
import org.prebid.server.hooks.modules.rule.engine.core.rules.Rule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionResult;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleConfig;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.RuleAction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.arguments.InfrastructureArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.arguments.ResultFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.Schema;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionHolder;
import org.prebid.server.hooks.v1.analytics.Activity;
import org.prebid.server.model.UpdateResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MatchingRequestRule implements Rule<BidRequest> {

    private final Schema<BidRequest> schema;
    private final RuleTree<RuleConfig<BidRequest>> ruleTree;

    public MatchingRequestRule(Schema<BidRequest> schema, RuleTree<RuleConfig<BidRequest>> ruleTree) {
        this.schema = Objects.requireNonNull(schema);
        this.ruleTree = Objects.requireNonNull(ruleTree);
    }

    @Override
    public RuleResult<BidRequest> process(BidRequest bidRequest) {
        final List<Imp> imps = ListUtils.emptyIfNull(bidRequest.getImp());
        final List<SchemaFunctionHolder<BidRequest>> schemaFunctionHolders = schema.getFunctions();

        final List<Activity> activities = new ArrayList<>();
        BidRequest updatedBidRequest = bidRequest;
        boolean updated = false;

        for (Imp imp : imps) {
            final List<String> matchers = schemaFunctionHolders.stream()
                    .map(holder -> holder.getSchemaFunction().extract(
                            SchemaFunctionArguments.of(bidRequest, holder.getArguments(), imp)))
                    .toList();

            final RuleConfig<BidRequest> ruleConfig = ruleTree.getValue(matchers);

            for (RuleAction<BidRequest> action : ruleConfig.getActions()) {
                final InfrastructureArguments infrastructureArguments = InfrastructureArguments.of(
                        null,
                        "analyticsKey",
                        ruleConfig.getRuleFired(),
                        "modelVersion");

                final ResultFunction<BidRequest> function = action.getFunction();

                for (ResultFunctionArguments arguments : action.getArguments()) {
                    final ResultFunctionResult<BidRequest> bidRequestResult = function.apply(
                            arguments, infrastructureArguments, updatedBidRequest);
                    final UpdateResult<BidRequest> updateResult = bidRequestResult.getUpdateResult();

                    updated = updateResult.isUpdated() || updated;
                    updatedBidRequest = updateResult.getValue();
                    activities.addAll(bidRequestResult.getAnalyticsTags().activities());
                }
            }
        }

        return RuleResult.of(UpdateResult.of(updated, updatedBidRequest), TagsImpl.of(activities));
    }
}
