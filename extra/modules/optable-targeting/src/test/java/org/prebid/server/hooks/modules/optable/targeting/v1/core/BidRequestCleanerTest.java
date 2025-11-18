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
}
