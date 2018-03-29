package org.prebid.server.bidder;

import org.junit.Before;
import org.junit.Test;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;

import static org.assertj.core.api.Assertions.assertThat;

public class DisabledBidderRequesterTest {

    private DisabledBidderRequester disabledBidderRequester;

    @Before
    public void setUp() {
        disabledBidderRequester = new DisabledBidderRequester("error message");
    }

    @Test
    public void makeHttpRequestsShouldRespondWithExpectedError() {
        // when
        final BidderSeatBid bidderSeatBid = disabledBidderRequester.requestBids(null, null).result();

        // then
        assertThat(bidderSeatBid.getBids()).isEmpty();
        assertThat(bidderSeatBid.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsOnly("error message");
    }
}
