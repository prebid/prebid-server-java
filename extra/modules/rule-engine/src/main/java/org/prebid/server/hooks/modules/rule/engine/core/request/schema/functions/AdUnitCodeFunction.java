package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.rule.engine.core.request.util.AdUnitCodeUtils;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;

public class AdUnitCodeFunction implements SchemaFunction<RequestContext> {

    public static final String NAME = "adUnitCode";

    @Override
    public String extract(SchemaFunctionArguments<RequestContext> arguments) {
        final RequestContext context = arguments.getOperand();
        final String impId = context.getImpId();
        final BidRequest bidRequest = context.getBidRequest();

        return ListUtils.emptyIfNull(bidRequest.getImp()).stream()
                .filter(imp -> StringUtils.equals(imp.getId(), impId))
                .findFirst()
                .flatMap(AdUnitCodeUtils::extractAdUnitCode)
                .orElse(UNDEFINED_RESULT);
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertNoArgs(config);
    }
}
