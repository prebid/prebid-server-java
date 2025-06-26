package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.User;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.util.List;

public class BidRequestCleaner implements PayloadUpdate<AuctionRequestPayload> {

    private static final String OPTABLE_FIELD = "optable";
    private static final List<String> FIELDS_TO_REMOVE = List.of("email", "phone", "zip", "vid");

    public static BidRequestCleaner instance() {
        return new BidRequestCleaner();
    }

    @Override
    public AuctionRequestPayload apply(AuctionRequestPayload payload) {
        return AuctionRequestPayloadImpl.of(clearExtUserOptable(payload.bidRequest()));
    }

    private static BidRequest clearExtUserOptable(BidRequest bidRequest) {
        final User user = bidRequest.getUser();
        final ExtUser extUser = user != null ? user.getExt() : null;
        final JsonNode optable = extUser != null ? extUser.getProperty(OPTABLE_FIELD) : null;
        if (optable == null || !optable.isObject() || optable.isEmpty()) {
            return bidRequest;
        }

        final ObjectNode cleanedOptable = cleanOptable((ObjectNode) optable);
        if (cleanedOptable.isEmpty()) {
            extUser.addProperty(OPTABLE_FIELD, null);
        } else {
            extUser.addProperty(OPTABLE_FIELD, cleanedOptable);
        }

        return bidRequest;
    }

    public static ObjectNode cleanOptable(ObjectNode optable) {
        return optable.deepCopy().remove(FIELDS_TO_REMOVE);
    }
}
