package org.prebid.server.hooks.modules.rule.engine.core.rules.request;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.collections4.ListUtils;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleTree;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionResult;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.RuleResult;
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

public class BasicRequestRule implements RequestRule {

    private final Schema<BidRequest> schema;
    private final RuleTree<RuleResult<Imp>> ruleTree;

    public BasicRequestRule(Schema<BidRequest> schema, RuleTree<RuleResult<Imp>> ruleTree) {
        this.schema = Objects.requireNonNull(schema);
        this.ruleTree = Objects.requireNonNull(ruleTree);
    }

    @Override
    public RequestRuleResult process(BidRequest bidRequest, boolean validation) {
        final List<Imp> imps = ListUtils.emptyIfNull(bidRequest.getImp());
        final List<SchemaFunctionHolder<BidRequest>> schemaFunctionHolders = schema.getFunctions();

        final List<Imp> updatedImps = new ArrayList<>();
        final List<Activity> activities = new ArrayList<>();
        boolean updated = false;

        for (Imp imp : imps) {
            final List<String> matchers = schemaFunctionHolders.stream()
                    .map(holder -> holder.getSchemaFunction().extract(
                            SchemaFunctionArguments.of(
                                    bidRequest, holder.getArguments(), false, imp)))
                    .toList();

            final RuleResult<Imp> ruleResult = ruleTree.getValue(matchers);
            final ResultFunction<Imp> action = ruleResult.getAction();
            final InfrastructureArguments infrastructureArguments = null;

            Imp updatedImp = imp;
            for (ResultFunctionArguments arguments : ruleResult.getArguments()) {
                final ResultFunctionResult<Imp> impResult = action.apply(
                        arguments, infrastructureArguments, updatedImp);
                final UpdateResult<Imp> updateResult = impResult.getUpdateResult();

                updated = updateResult.isUpdated() || updated;
                updatedImp = updateResult.getValue();
                activities.addAll(impResult.getAnalyticsTags().activities());
            }

            updatedImps.add(updatedImp);
        }

        final UpdateResult<BidRequest> result = updated
                ? UpdateResult.updated(bidRequest.toBuilder().imp(updatedImps).build())
                : UpdateResult.unaltered(bidRequest);

        return RequestRuleResult.of(result, TagsImpl.of(activities));
    }
}
