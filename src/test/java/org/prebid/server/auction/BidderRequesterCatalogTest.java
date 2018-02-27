package org.prebid.server.auction;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.bidder.BidderRequester;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

public class BidderRequesterCatalogTest {

    private static final String BIDDER_REQUESTER = "rubicon";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderRequester bidderRequester;

    private BidderRequesterCatalog bidderRequesterCatalog;

    @Before
    public void setUp() {
        given(bidderRequester.name()).willReturn(BIDDER_REQUESTER);

        bidderRequesterCatalog = new BidderRequesterCatalog(singletonList(bidderRequester));
    }

    @Test
    public void isValidNameShouldReturnTrueForKnownHttpConnector() {
        // when and then
        assertThat(bidderRequesterCatalog.isValidName(BIDDER_REQUESTER)).isTrue();
    }

    @Test
    public void isValidNameShouldReturnFalseForUnknownHttpConnector() {
        // when and then
        assertThat(bidderRequesterCatalog.isValidName("unknown_bidder")).isFalse();
    }

    @Test
    public void byNameShouldReturnHttpConnector() {
        // when and then
        assertThat(bidderRequesterCatalog.byName(BIDDER_REQUESTER)).isSameAs(bidderRequester);
    }

    @Test
    public void byNameShouldReturnNullForUnknownHttpConnector() {
        // when and then
        assertThat(bidderRequesterCatalog.byName("unknown_bidderRequester")).isNull();
    }
}
