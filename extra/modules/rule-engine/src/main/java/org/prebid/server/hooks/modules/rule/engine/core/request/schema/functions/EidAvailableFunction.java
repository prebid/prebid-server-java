package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.User;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class EidAvailableFunction implements SchemaFunction<RequestContext> {

    public static final String NAME = "eidAvailable";

    @Override
    public String extract(SchemaFunctionArguments<RequestContext> arguments) {
        final boolean available = Optional.of(arguments.getOperand().getBidRequest())
                .map(BidRequest::getUser)
                .map(User::getEids)
                .filter(Predicate.not(List::isEmpty))
                .isPresent();

        return Boolean.toString(available);
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertNoArgs(config);
    }
}
