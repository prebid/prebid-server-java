package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;

public class DomainFunction implements SchemaFunction<RequestContext> {

    public static final String NAME = "domain";

    @Override
    public String extract(SchemaFunctionArguments<RequestContext> arguments) {
        final BidRequest bidRequest = arguments.getOperand().getBidRequest();

        return UNDEFINED_RESULT;
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertNoArgs(config);
    }
}
