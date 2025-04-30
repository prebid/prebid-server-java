package org.prebid.server.hooks.modules.rule.engine.core.rules.request.result.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionArguments;
import org.prebid.server.model.UpdateResult;

import java.util.Collections;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class IncludeBiddersFunction implements ResultFunction<BidRequest> {

    public static final IncludeBiddersFunction INSTANCE = new IncludeBiddersFunction();

    public static final String NAME = "includeBidders";

    @Override
    public RuleResult<BidRequest> apply(ResultFunctionArguments<BidRequest> arguments) {
        return RuleResult.of(
                UpdateResult.unaltered(arguments.getOperand()),
                TagsImpl.of(Collections.emptyList()));
    }

    @Override
    public void validateConfigArguments(List<JsonNode> configArguments) {
    }
}
