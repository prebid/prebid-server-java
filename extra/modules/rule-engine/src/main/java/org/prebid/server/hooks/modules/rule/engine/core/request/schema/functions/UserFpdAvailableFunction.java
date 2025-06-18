package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.User;
import org.prebid.server.hooks.modules.rule.engine.core.request.context.RequestSchemaContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class UserFpdAvailableFunction implements SchemaFunction<RequestSchemaContext> {

    public static final String NAME = "userFpdAvailable";

    @Override
    public String extract(SchemaFunctionArguments<RequestSchemaContext> arguments) {
        final Optional<User> user = Optional.of(arguments.getOperand().getBidRequest())
                .map(BidRequest::getUser);

        final boolean userDataAvailable = user.map(User::getData)
                .filter(Predicate.not(List::isEmpty))
                .isPresent();

        final boolean userExtDataAvailable = user.map(User::getExt)
                .map(ExtUser::getData)
                .filter(Predicate.not(ObjectNode::isEmpty))
                .isPresent();

        return Boolean.toString(userDataAvailable || userExtDataAvailable);
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertNoArgs(config);
    }
}
