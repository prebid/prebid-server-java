package org.prebid.server.hooks.modules.rule.engine.core.request.result.functions.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleAction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;
import org.prebid.server.hooks.v1.analytics.Tags;

import java.util.Collections;
import java.util.Objects;

public class LogATagFunction implements ResultFunction<BidRequest, RequestRuleContext> {

    public static final String NAME = "logAtag";

    private static final String ANALYTICS_VALUE_FIELD = "analyticsValue";

    private final ObjectMapper mapper;

    public LogATagFunction(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public RuleResult<BidRequest> apply(ResultFunctionArguments<BidRequest, RequestRuleContext> arguments) {
        final Tags tags = AnalyticsMapper.toTags(
                mapper,
                arguments.getInfrastructureArguments(),
                arguments.getConfig().get(ANALYTICS_VALUE_FIELD).asText());

        return RuleResult.of(arguments.getOperand(), RuleAction.NO_ACTION, tags, Collections.emptyList());
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertString(config, ANALYTICS_VALUE_FIELD);
    }
}
