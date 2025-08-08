package org.prebid.server.hooks.modules.rule.engine.core.request;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.hooks.modules.rule.engine.core.rules.ConditionMatchingRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.MatchingRuleFactory;
import org.prebid.server.hooks.modules.rule.engine.core.rules.Rule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleConfig;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.Schema;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionHolder;
import org.prebid.server.hooks.modules.rule.engine.core.rules.tree.RuleTree;

public class RequestMatchingRuleFactory implements MatchingRuleFactory<BidRequest, RequestRuleContext> {

    @Override
    public Rule<BidRequest, RequestRuleContext> create(
            Schema<BidRequest, RequestRuleContext> schema,
            RuleTree<RuleConfig<BidRequest, RequestRuleContext>> ruleTree,
            String analyticsKey,
            String modelVersion) {

        final ConditionMatchingRule<BidRequest, RequestRuleContext> requestMatchingRule = new ConditionMatchingRule<>(
                schema, ruleTree, analyticsKey, modelVersion);

        return schema.getFunctions().stream()
                .map(SchemaFunctionHolder::getName)
                .anyMatch(RequestStageSpecification.PER_IMP_SCHEMA_FUNCTIONS::contains)

                ? new PerImpMatchingRule(requestMatchingRule)
                : requestMatchingRule;
    }
}
