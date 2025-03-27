package org.prebid.server.hooks.modules.optable.targeting.v1.core.merger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.User;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Data;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class BidRequestBuilder {

    private BidRequest bidRequest;

    public BidRequestBuilder(BidRequest bidRequest) {
        this.bidRequest = Objects.requireNonNull(bidRequest);
    }

    public BidRequestBuilder addEids(List<Eid> eids) {
        if (bidRequest == null || CollectionUtils.isEmpty(eids)) {
            return this;
        }

        final User user = getOrCreateUser(bidRequest);
        bidRequest = bidRequest.toBuilder()
                .user(user.toBuilder()
                        .eids(EidsMerger.merge(user.getEids(), eids))
                        .build())
                .build();

        return this;
    }

    public BidRequestBuilder addData(List<Data> data) {
        if (bidRequest == null || CollectionUtils.isEmpty(data)) {
            return this;
        }

        final User user = getOrCreateUser(bidRequest);

        bidRequest = bidRequest.toBuilder()
                .user(user.toBuilder()
                        .data(DataMerger.merge(user.getData(), data))
                        .build())
                .build();

        return this;
    }

    private static Optional<User> getUserOpt(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest)
                .map(BidRequest::getUser);
    }

    private static User getOrCreateUser(BidRequest bidRequest) {
        return getUserOpt(bidRequest)
                .orElseGet(() -> User.builder().eids(new ArrayList<>()).build());
    }

    public BidRequest build() {
        return bidRequest;
    }

    public BidRequestBuilder clearExtUserOptable() {
        if (bidRequest == null) {
            return this;
        }

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
            bidRequest = bidRequest.toBuilder()
                    .user(userOpt.get().toBuilder()
                            .ext(extUser)
                            .build())
                    .build();
        }

        return this;
    }
}
