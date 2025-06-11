package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.User;
import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.merger.PayloadCleaner;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.util.Optional;

@Accessors(fluent = true)
@Value(staticConstructor = "instance")
public class BidRequestCleaner implements PayloadUpdate<AuctionRequestPayload> {

    @Override
    public AuctionRequestPayload apply(AuctionRequestPayload payload) {
        return AuctionRequestPayloadImpl.of(clearExtUserOptable(payload.bidRequest()));
    }

    private static BidRequest clearExtUserOptable(BidRequest bidRequest) {
        final Optional<User> userOpt = getUserOpt(bidRequest);

        final JsonNode userExtOptable = userOpt.map(User::getExt)
                .map(it -> it.getProperty("optable"))
                .map(it -> PayloadCleaner.cleanUserExtOptable((ObjectNode) it))
                .orElse(null);

        if (userExtOptable != null) {
            final ExtUser extUser = userOpt.map(User::getExt).orElse(null);
            if (!userExtOptable.isEmpty()) {
                extUser.addProperty("optable", userExtOptable);
            } else {
                extUser.addProperty("optable", null);
            }
            return bidRequest.toBuilder()
                    .user(userOpt.get().toBuilder()
                            .ext(extUser)
                            .build())
                    .build();
        }

        return bidRequest;
    }

    private static Optional<User> getUserOpt(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest)
                .map(BidRequest::getUser);
    }
}
