package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.User;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.optable.targeting.v1.BaseOptableTest;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

import static org.assertj.core.api.Assertions.assertThat;

public class BidRequestCleanerTest extends BaseOptableTest {

    @Test
    public void shouldRemoveUserExtOptableTag() {
        // given
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(givenBidRequest(bidRequest ->
                bidRequest.user(givenUser())));

        // when
        final AuctionRequestPayload result = BidRequestCleaner.instance().apply(auctionRequestPayload);

        // then
        assertThat(result).extracting(AuctionRequestPayload::bidRequest)
                .extracting(BidRequest::getUser)
                .extracting(User::getExt)
                .extracting(it -> it.getProperty("optable"))
                .isEqualTo(null);
    }

    @Test
    public void shouldKeepOtherUserExtOptableTags() {
        // given
        final User user = givenUser();
        ((com.fasterxml.jackson.databind.node.ObjectNode) user.getExt().getProperty("optable"))
                .put("other", "value");

        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(givenBidRequest(bidRequest ->
                bidRequest.user(user)));

        // when
        final AuctionRequestPayload result = BidRequestCleaner.instance().apply(auctionRequestPayload);

        // then
        assertThat(result).extracting(AuctionRequestPayload::bidRequest)
                .extracting(BidRequest::getUser)
                .extracting(User::getExt)
                .extracting(it -> (com.fasterxml.jackson.databind.node.ObjectNode) it.getProperty("optable"))
                .isNotNull()
                .satisfies(optable -> {
                    assertThat(optable.has("other")).isTrue();
                    assertThat(optable.has("email")).isFalse();
                });
    }
}
