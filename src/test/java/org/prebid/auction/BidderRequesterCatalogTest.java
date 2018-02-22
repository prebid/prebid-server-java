package org.prebid.auction;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.bidder.BidderRequester;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

public class BidderRequesterCatalogTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderRequester bidderRequester;

    private BidderRequesterCatalog bidderRequesterCatalog;

    @Before
    public void setUp() {
        given(bidderRequester.name()).willReturn("BidderName");
        bidderRequesterCatalog = new BidderRequesterCatalog(singletonList(bidderRequester));
    }

    @Test
    public void isValidNameShouldReturnTrueForKnownHttpConnector() {
        assertThat(bidderRequesterCatalog.isValidName("BidderName")).isTrue();
    }

    @Test
    public void isValidNameShouldReturnFalseForUnknownHttpConnector() {
        assertThat(bidderRequesterCatalog.isValidName("unknown_bidder")).isFalse();
    }

    @Test
    public void byNameShouldReturnHttpConnector() {
        // when
        final BidderRequester bidderRequesterFromCatalog = bidderRequesterCatalog.byName("BidderName");

        // then
        assertThat(bidderRequesterFromCatalog).isSameAs(bidderRequester);
    }

    @Test
    public void byNameShouldReturnNullForUnknownHttpConnector() {
        // when
        final BidderRequester bidderRequesterFromCatalog = bidderRequesterCatalog.byName("unknown_bidder");

        // then
        assertThat(bidderRequesterFromCatalog).isNull();
    }
}
