package org.prebid.server.hooks.modules.rule.engine.core.rules.request.result.functions;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.arguments.InfrastructureArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionArguments;
import org.prebid.server.model.UpdateResult;

import java.util.Collections;

public class IncludeBiddersFunction implements ResultFunction<BidRequest> {


    @Override
    public RuleResult<BidRequest> apply(ResultFunctionArguments arguments,
                                        InfrastructureArguments infrastructureArguments,
                                        BidRequest operand) {

        return RuleResult.of(UpdateResult.unaltered(operand), TagsImpl.of(Collections.emptyList()));
    }

    @Override
    public void validateArguments(ResultFunctionArguments arguments) {
        arguments
    }
}
