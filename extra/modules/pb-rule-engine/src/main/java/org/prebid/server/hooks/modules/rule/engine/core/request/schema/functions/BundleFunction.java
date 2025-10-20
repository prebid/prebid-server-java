package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;

import java.util.Optional;

public class BundleFunction implements SchemaFunction<BidRequest, RequestRuleContext> {

    public static final String NAME = "bundle";

    @Override
    public String extract(SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments) {
        return Optional.ofNullable(arguments.getOperand().getApp())
                .map(App::getBundle)
                .orElse(UNDEFINED_RESULT);
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertNoArgs(config);
    }
}
