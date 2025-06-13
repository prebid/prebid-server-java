package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Eid;
import lombok.Value;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Data;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Ortb2;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.User;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.merger.DataMerger;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.merger.EidsMerger;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class BidRequestEnricher implements PayloadUpdate<AuctionRequestPayload> {

    TargetingResult targetingResult;

    @Override
    public AuctionRequestPayload apply(AuctionRequestPayload payload) {
        return AuctionRequestPayloadImpl.of(enrichBidRequest(payload.bidRequest(), targetingResult));
    }

    private static BidRequest enrichBidRequest(BidRequest bidRequest, TargetingResult targetingResults) {
        if (bidRequest == null || targetingResults == null) {
            return bidRequest;
        }

        final User optableUser = getUser(targetingResults);
        if (optableUser == null) {
            return bidRequest;
        }

        final com.iab.openrtb.request.User bidRequestUser = getOrCreateUser(bidRequest);

        return bidRequest.toBuilder()
                .user(mergeUserData(bidRequestUser, optableUser))
                .build();
    }

    private static com.iab.openrtb.request.User mergeUserData(com.iab.openrtb.request.User user, User optableUser) {
        final com.iab.openrtb.request.User.UserBuilder userBuilder = user.toBuilder();

        final List<Eid> eids = optableUser.getEids();
        final List<Data> data = optableUser.getData();

        if (!CollectionUtils.isEmpty(eids)) {
            userBuilder.eids(EidsMerger.merge(user.getEids(), eids));
        }

        if (!CollectionUtils.isEmpty(data)) {
            userBuilder.data(DataMerger.merge(user.getData(), data));
        }

        return userBuilder.build();
    }

    private static User getUser(TargetingResult targetingResults) {
        return Optional.ofNullable(targetingResults)
                .map(TargetingResult::getOrtb2)
                .map(Ortb2::getUser)
                .orElse(null);
    }

    private static com.iab.openrtb.request.User getOrCreateUser(BidRequest bidRequest) {
        return getUserOpt(bidRequest)
                .orElseGet(() -> com.iab.openrtb.request.User.builder().eids(new ArrayList<>()).build());
    }

    private static Optional<com.iab.openrtb.request.User> getUserOpt(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest)
                .map(BidRequest::getUser);
    }
}
