package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.User;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.optable.targeting.v1.BaseOptableTest;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

import static org.assertj.core.api.Assertions.assertThat;

public class AuctionRequestCleanerTest extends BaseOptableTest {

    @Test
    public void shouldReturnSameCleanerInstance() {
        // when and then
        assertThat(AuctionRequestCleaner.instance()).isSameAs(AuctionRequestCleaner.instance());
    }

    @Test
    public void shouldRemoveUserExtOptableTag() {
        // given
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(givenBidRequest(bidRequest ->
                bidRequest.user(givenUser())));

        // when
        final AuctionRequestPayload result = AuctionRequestCleaner.instance().apply(auctionRequestPayload);

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
                .put("other", "value")
                .put("id5_signature", "signature");

        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(givenBidRequest(bidRequest ->
                bidRequest.user(user)));

        // when
        final AuctionRequestPayload result = AuctionRequestCleaner.instance().apply(auctionRequestPayload);

        // then
        assertThat(result).extracting(AuctionRequestPayload::bidRequest)
                .extracting(BidRequest::getUser)
                .extracting(User::getExt)
                .extracting(it -> (com.fasterxml.jackson.databind.node.ObjectNode) it.getProperty("optable"))
                .isNotNull()
                .satisfies(optable -> {
                    assertThat(optable.has("other")).isTrue();
                    assertThat(optable.has("email")).isFalse();
                    assertThat(optable.has("id5_signature")).isFalse();
                });
    }

    @Test
    public void shouldKeepOtherUserExtProperties() {
        // given
        final User user = givenUser();
        user.getExt().addProperty("other", com.fasterxml.jackson.databind.node.TextNode.valueOf("value"));

        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(givenBidRequest(bidRequest ->
                bidRequest.user(user)));

        // when
        final AuctionRequestPayload result = AuctionRequestCleaner.instance().apply(auctionRequestPayload);

        // then
        assertThat(result).extracting(AuctionRequestPayload::bidRequest)
                .extracting(BidRequest::getUser)
                .extracting(User::getExt)
                .extracting(it -> it.getProperty("other"))
                .isEqualTo(com.fasterxml.jackson.databind.node.TextNode.valueOf("value"));
    }

    @Test
    public void shouldNotModifyOriginalRequestWhenCleaning() {
        // given
        final User user = givenUser();
        final BidRequest bidRequest = givenBidRequest(request -> request.user(user));

        // when
        final AuctionRequestPayload result = AuctionRequestCleaner.instance()
                .apply(AuctionRequestPayloadImpl.of(bidRequest));

        // then
        assertThat(result.bidRequest()).isNotSameAs(bidRequest);
        assertThat(result.bidRequest().getUser()).isNotSameAs(user);
        final com.fasterxml.jackson.databind.node.ObjectNode originalOptable =
                (com.fasterxml.jackson.databind.node.ObjectNode) user.getExt().getProperty("optable");
        assertThat(originalOptable.has("email")).isTrue();
        assertThat(originalOptable.has("phone")).isTrue();
        assertThat(originalOptable.has("zip")).isTrue();
        assertThat(originalOptable.has("vid")).isTrue();
    }

    @Test
    public void shouldReturnSameBidRequestWhenUserExtHasNoOptable() {
        // given
        final BidRequest bidRequest = givenBidRequest(request ->
                request.user(User.builder().build()));

        // when
        final AuctionRequestPayload result = AuctionRequestCleaner.instance()
                .apply(AuctionRequestPayloadImpl.of(bidRequest));

        // then
        assertThat(result.bidRequest()).isSameAs(bidRequest);
    }
}
