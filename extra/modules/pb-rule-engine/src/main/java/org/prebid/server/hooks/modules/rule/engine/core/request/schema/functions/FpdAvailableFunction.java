package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import org.prebid.server.hooks.modules.rule.engine.core.request.context.RequestSchemaContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class FpdAvailableFunction implements SchemaFunction<RequestSchemaContext> {

    public static final String NAME = "fpdAvailable";

    @Override
    public String extract(SchemaFunctionArguments<RequestSchemaContext> arguments) {
        final BidRequest bidRequest = arguments.getOperand().getBidRequest();

        final boolean available = isUserDataAvailable(bidRequest)
                || isUserExtDataAvailable(bidRequest)
                || isSiteContentDataAvailable(bidRequest)
                || isSiteExtDataAvailable(bidRequest)
                || isAppContentDataAvailable(bidRequest)
                || isAppExtDataAvailable(bidRequest);

        return Boolean.toString(available);
    }

    private static boolean isUserDataAvailable(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getUser())
                .map(User::getData)
                .filter(Predicate.not(List::isEmpty))
                .isPresent();
    }

    private static boolean isUserExtDataAvailable(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getUser())
                .map(User::getExt)
                .map(ExtUser::getData)
                .filter(Predicate.not(ObjectNode::isEmpty))
                .isPresent();
    }

    private static boolean isSiteContentDataAvailable(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getSite())
                .map(Site::getContent)
                .map(Content::getData)
                .filter(Predicate.not(List::isEmpty))
                .isPresent();
    }

    private static boolean isSiteExtDataAvailable(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getSite())
                .map(Site::getExt)
                .map(ExtSite::getData)
                .filter(Predicate.not(ObjectNode::isEmpty))
                .isPresent();
    }

    private static boolean isAppContentDataAvailable(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getApp())
                .map(App::getContent)
                .map(Content::getData)
                .filter(Predicate.not(List::isEmpty))
                .isPresent();
    }

    private static boolean isAppExtDataAvailable(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getApp())
                .map(App::getExt)
                .map(ExtApp::getData)
                .filter(Predicate.not(ObjectNode::isEmpty))
                .isPresent();
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertNoArgs(config);
    }
}
