package org.prebid.server.auction;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.bidder.Bidder;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class BidderCatalogTest {

    private static final String BIDDER = "rubicon";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private BidderCatalog bidderCatalog;

    @Test
    public void isValidNameShouldReturnTrueForKnownBidders() {
        // given
        final Bidder bidder = mock(Bidder.class);
        given(bidder.name()).willReturn(BIDDER);

        bidderCatalog = new BidderCatalog(singletonList(bidder));

        // when and then
        assertThat(bidderCatalog.isValidName(BIDDER)).isTrue();
    }

    @Test
    public void isValidNameShouldReturnFalseForUnknownBidders() {
        // given
        bidderCatalog = new BidderCatalog(emptyList());

        // when and then
        assertThat(bidderCatalog.isValidName("unknown_bidder")).isFalse();
    }

    @Test
    public void byNameShouldReturnBidderNameForKnownBidder() {
        // given
        final Bidder bidder = mock(Bidder.class);
        given(bidder.name()).willReturn(BIDDER);

        bidderCatalog = new BidderCatalog(singletonList(bidder));

        // when and then
        assertThat(bidderCatalog.byName(BIDDER)).isEqualTo(bidder);
    }

    @Test
    public void byNameShouldReturnNullForUnknownBidder() {
        // given
        bidderCatalog = new BidderCatalog(emptyList());

        // when and then
        assertThat(bidderCatalog.byName("unknown_bidder")).isNull();
    }
}
