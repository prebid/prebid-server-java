package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Regs;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

public class GppSidAvailableFunction implements SchemaFunction<BidRequest, RequestRuleContext> {

    public static final String NAME = "gppSidAvailable";

    @Override
    public String extract(SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments) {
        final boolean available = Optional.of(arguments.getOperand())
                .map(BidRequest::getRegs)
                .map(Regs::getGppSid)
                .stream()
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .anyMatch(sid -> sid > 0);

        return Boolean.toString(available);
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertNoArgs(config);
    }
}
