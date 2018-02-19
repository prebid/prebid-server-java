package org.rtb.vexing.auction;

import com.iab.openrtb.request.BidRequest;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.bidder.Bidder;
import org.rtb.vexing.bidder.model.BidderBid;
import org.rtb.vexing.bidder.model.HttpCall;
import org.rtb.vexing.bidder.model.HttpRequest;
import org.rtb.vexing.bidder.model.Result;

import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class BidderCatalogTest {

    private static final String RUBICON = "rubicon";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private BidderCatalog bidderCatalog;

    @Test
    public void isValidNameShouldReturnTrueForKnownBidders() {
        // given
        bidderCatalog = new BidderCatalog(singletonList(createBidder()));

        // when and then
        assertThat(bidderCatalog.isValidName(RUBICON)).isTrue();
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
        final Bidder bidder = createBidder();
        bidderCatalog = new BidderCatalog(singletonList(bidder));


        // when and then
        assertThat(bidderCatalog.byName(RUBICON)).isEqualTo(bidder);
    }

    @Test
    public void byNameShouldReturnNullForUnknownBidder() {
        // given
        bidderCatalog = new BidderCatalog(emptyList());

        // when and then
        assertThat(bidderCatalog.byName("unknown_bidder")).isNull();
    }

    private Bidder createBidder() {
        return new Bidder() {
            @Override
            public Result<List<HttpRequest>> makeHttpRequests(BidRequest request) {
                return null;
            }

            @Override
            public Result<List<BidderBid>> makeBids(HttpCall httpCall, BidRequest bidRequest) {
                return null;
            }

            @Override
            public String name() {
                return RUBICON;
            }

            @Override
            public String cookieFamilyName() {
                return null;
            }
        };
    }
}