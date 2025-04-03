package org.prebid.server.hooks.modules.rule.engine.core.rules.request;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.collections4.ListUtils;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleTree;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionHolder;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionHolder;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RequestRule {

    private static final Map<String, SchemaFunction<BidRequest>> SCHEMA_FUNCTIONS = Map.of(
            "deviceCountry", RequestSchema::deviceCountryExtractor);
    private static final Map<String, ResultFunction<BidRequest, RequestRuleResult>> RESULT_FUNCTIONS = Map.of();

    private final List<SchemaFunctionHolder<BidRequest>> schemaFunctionHolders;
    private final RuleTree<ResultFunctionHolder<RequestRuleResult>> ruleTree;

    public RequestRule(List<SchemaFunctionHolder<BidRequest>> schemaFunctionHolders,
                       RuleTree<ResultFunctionHolder<RequestRuleResult>> ruleTree) {

        this.schemaFunctionHolders = Objects.requireNonNull(schemaFunctionHolders);
        this.ruleTree = Objects.requireNonNull(ruleTree);
    }

    public RequestRuleResult process(BidRequest bidRequest) {
        final List<Imp> imps = ListUtils.emptyIfNull(bidRequest.getImp());


        for (Imp imp : imps) {
            final List<String> schema = schemaFunctionHolders.stream()
                    .map(holder -> holder.getSchemaFunction().extract(
                            SchemaFunctionArguments.of(
                                    bidRequest, holder.getArguments(), false, imp)))
                    .toList();

            final ResultFunctionHolder<RequestRuleResult> result = ruleTree.getValue(schema);
            return result.getFunction().apply(result.getArguments());
        }
    }
}
