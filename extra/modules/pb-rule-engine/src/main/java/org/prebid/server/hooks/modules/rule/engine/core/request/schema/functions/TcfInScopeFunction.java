package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Regs;
import org.prebid.server.hooks.modules.rule.engine.core.request.context.RequestSchemaContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;

import java.util.Optional;

public class TcfInScopeFunction implements SchemaFunction<RequestSchemaContext> {

    public static final String NAME = "tcfInScope";

    @Override
    public String extract(SchemaFunctionArguments<RequestSchemaContext> arguments) {
        final boolean inScope = Optional.of(arguments.getOperand().getBidRequest())
                .map(BidRequest::getRegs)
                .map(Regs::getGdpr)
                .filter(Integer.valueOf(1)::equals)
                .isPresent();

        return Boolean.toString(inScope);
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertNoArgs(config);
    }
}
