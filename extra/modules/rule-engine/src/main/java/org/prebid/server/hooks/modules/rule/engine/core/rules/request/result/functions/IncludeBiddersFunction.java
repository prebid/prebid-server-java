package org.prebid.server.hooks.modules.rule.engine.core.rules.request.result.functions;

import com.iab.openrtb.request.Imp;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionResult;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.arguments.InfrastructureArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.arguments.ResultFunctionArguments;
import org.prebid.server.model.UpdateResult;

import java.util.Collections;

public class IncludeBiddersFunction implements ResultFunction<Imp> {


    @Override
    public ResultFunctionResult<Imp> apply(ResultFunctionArguments arguments,
                                           InfrastructureArguments infrastructureArguments,
                                           Imp operand) {


        return ResultFunctionResult.of(UpdateResult.unaltered(operand), TagsImpl.of(Collections.emptyList()));
    }
}
