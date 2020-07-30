package org.prebid.server.auction;

import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpTargeting;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;

public class WinningBidsResolverTest extends VertxTest {

    private WinningBidsResolver winningBidsResolver = new WinningBidsResolver(jacksonMapper);

    @Test
    public void resolveWinningBidsPerImpBidderShouldReduceBidsByPriceDroppingNonDealsBids() {
        // given
        final BidderResponse bidderResponse = BidderResponse.of("bidder1",
                givenSeatBid(
                        givenBidderBid("bidId1", "impId1", "dealId1", 5.0f),
                        givenBidderBid("bidId2", "impId1", "dealId2", 6.0f),
                        givenBidderBid("bidId3", "impId1", null, 7.0f)
                ), 0);

        final Imp imp = givenImp("impId1", null);

        // when
        final BidderResponse resultBidderResponse = winningBidsResolver.resolveWinningBidsPerImpBidder(bidderResponse,
                singletonList(imp), true);

        // then
        assertThat(resultBidderResponse.getSeatBid().getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId)
                .containsOnly("bidId2");
    }

    @Test
    public void resolveWinningBidsPerImpBidderReduceDealsAndNonDealsBidsByPriceImpPreferDealsFalseAndAccountTrue() {
        // given
        final BidderResponse bidderResponse = BidderResponse.of("bidder1",
                givenSeatBid(
                        givenBidderBid("bidId1", "impId1", "dealId1", 5.0f),
                        givenBidderBid("bidId2", "impId1", "dealId2", 6.0f),
                        givenBidderBid("bidId3", "impId1", null, 7.0f)
                        ), 0);

        final Imp imp = givenImp("impId1", false);

        // when
        final BidderResponse resultBidderResponse = winningBidsResolver.resolveWinningBidsPerImpBidder(bidderResponse,
                singletonList(imp), true);

        // then
        assertThat(resultBidderResponse.getSeatBid().getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId)
                .containsOnly("bidId3");
    }

    @Test
    public void resolveWinningBidsPerImpBidderReducePreferDealsWhenImpPreferDealsTrueAndAccountFalse() {
        // given
        final BidderResponse bidderResponse = BidderResponse.of("bidder1",
                givenSeatBid(
                        givenBidderBid("bidId1", "impId1", "dealId1", 5.0f),
                        givenBidderBid("bidId2", "impId1", "dealId2", 6.0f),
                        givenBidderBid("bidId3", "impId1", null, 7.0f)
                ), 0);

        final Imp imp = givenImp("impId1", true);

        // when
        final BidderResponse resultBidderResponse = winningBidsResolver.resolveWinningBidsPerImpBidder(bidderResponse,
                singletonList(imp), false);

        // then
        assertThat(resultBidderResponse.getSeatBid().getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId)
                .containsOnly("bidId2");
    }

    @Test
    public void resolveWinningBidsPerImpBidderShouldReduceNonDealBidsByPrice() {
        // given
        final BidderResponse bidderResponse = BidderResponse.of("bidder1",
                givenSeatBid(
                        givenBidderBid("bidId1", "impId1", null, 5.0f),
                        givenBidderBid("bidId2", "impId1", null, 6.0f),
                        givenBidderBid("bidId3", "impId1", null, 7.0f)
                ), 0);

        final Imp imp = givenImp("impId1", true);

        // when
        final BidderResponse resultBidderResponse = winningBidsResolver.resolveWinningBidsPerImpBidder(bidderResponse,
                singletonList(imp), true);

        // then
        assertThat(resultBidderResponse.getSeatBid().getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId)
                .containsOnly("bidId3");
    }

    @Test
    public void resolveWinningBidsPerImpBidderShouldNotReduceBids() {
        // given
        final BidderResponse bidderResponse = BidderResponse.of("bidder1",
                givenSeatBid(givenBidderBid("bidId1", "impId1", null, 5.0f)), 0);

        final Imp imp = givenImp("impId1", true);

        // when
        final BidderResponse resultBidderResponse = winningBidsResolver.resolveWinningBidsPerImpBidder(bidderResponse,
                singletonList(imp), true);

        // then
        assertThat(bidderResponse).isSameAs(resultBidderResponse);
    }

    @Test
    public void resolveWinningBidsPerImpBidderShouldReduceAllTypesOfBidsForMultipleImps() {
        // given
        final BidderResponse bidderResponse = BidderResponse.of("bidder1",
                givenSeatBid(
                        givenBidderBid("bidId1-1", "impId1", "dealId3", 5.0f), // deal
                        givenBidderBid("bidId2-1", "impId1", null, 5.5f), // non deal
                        givenBidderBid("bidId1-2", "impId2", "dealId4", 5.0f), // deal
                        givenBidderBid("bidId2-2", "impId2", "dealId5", 6.0f), // deal
                        givenBidderBid("bidId3-2", "impId2", null, 7.0f) // non deal
                ), 0);

        final Imp imp1 = givenImp("impId1", true);
        final Imp imp2 = givenImp("impId2", false);

        // when
        final BidderResponse resultBidderResponse = winningBidsResolver.resolveWinningBidsPerImpBidder(bidderResponse,
                Arrays.asList(imp1, imp2), true);

        // then
        assertThat(resultBidderResponse.getSeatBid().getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId)
                .containsOnly("bidId1-1", "bidId3-2");
    }

    @Test
    public void resolveWinningBidsShouldReturnHighestPriceDealBidWhenPreferDealsTrue() {
        // given
        final Bid firstBid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).dealid("d1").impid("i1")
                .build();
        final Bid secondBid = Bid.builder().id("bidId2").price(BigDecimal.valueOf(7.25)).dealid("d2").impid("i1")
                .build();
        final Bid thirdBid = Bid.builder().id("bidId3").price(BigDecimal.valueOf(8.25)).dealid("d3").impid("i1")
                .build();
        final Bid fourthBid = Bid.builder().id("bidId4").price(BigDecimal.valueOf(9.25)).impid("i1").build();

        final List<Imp> imps = singletonList(givenImp("i1", true));

        final List<BidderResponse> bidderResponses = givenBidderResponse(firstBid, secondBid, thirdBid, fourthBid);

        // when
        final Set<Bid> winningBids = winningBidsResolver.resolveWinningBids(bidderResponses, imps, true);

        // then
        assertThat(winningBids)
                .extracting(Bid::getId)
                .containsOnly("bidId3");
    }

    @Test
    public void resolveWinningBidsShouldReturnHighestPriceWhenPreferDealsFalse() {
        // given
        final Bid firstBid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).dealid("d1").impid("i1")
                .build();
        final Bid secondBid = Bid.builder().id("bidId2").price(BigDecimal.valueOf(7.25)).dealid("d2").impid("i1")
                .build();
        final Bid thirdBid = Bid.builder().id("bidId3").price(BigDecimal.valueOf(8.25)).dealid("d3").impid("i1")
                .build();
        final Bid fourthBid = Bid.builder().id("bidId4").price(BigDecimal.valueOf(9.25)).impid("i1").build();

        final List<Imp> imps = singletonList(givenImp("i1", false));

        final List<BidderResponse> bidderResponses = givenBidderResponse(firstBid, secondBid, thirdBid, fourthBid);

        // when
        final Set<Bid> winningBids = winningBidsResolver.resolveWinningBids(bidderResponses, imps, false);

        // then
        assertThat(winningBids)
                .extracting(Bid::getId)
                .containsOnly("bidId4");
    }

    @Test
    public void resolveWinningBidsShouldReturnHighestPriceDealWhenAccountDoesNotPreferAndImpPreferDeals() {
        // given
        final Bid firstBid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).dealid("d1").impid("i1")
                .build();
        final Bid secondBid = Bid.builder().id("bidId2").price(BigDecimal.valueOf(7.25)).dealid("d2").impid("i1")
                .build();
        final Bid thirdBid = Bid.builder().id("bidId3").price(BigDecimal.valueOf(8.25)).dealid("d3").impid("i1")
                .build();
        final Bid fourthBid = Bid.builder().id("bidId4").price(BigDecimal.valueOf(9.25)).impid("i1").build();

        final List<Imp> imps = singletonList(givenImp("i1", true));

        final List<BidderResponse> bidderResponses = givenBidderResponse(firstBid, secondBid, thirdBid, fourthBid);

        // when
        final Set<Bid> winningBids = winningBidsResolver.resolveWinningBids(bidderResponses, imps, false);

        // then
        assertThat(winningBids)
                .extracting(Bid::getId)
                .containsOnly("bidId3");
    }

    @Test
    public void resolveWinningBidsShouldChooseHighestPriceAmongNonDealBids() {
        // given
        final Bid firstBid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid("i1")
                .build();
        final Bid secondBid = Bid.builder().id("bidId2").price(BigDecimal.valueOf(7.25)).impid("i1")
                .build();
        final Bid thirdBid = Bid.builder().id("bidId3").price(BigDecimal.valueOf(8.25)).impid("i1")
                .build();
        final Bid fourthBid = Bid.builder().id("bidId4").price(BigDecimal.valueOf(9.25)).impid("i1").build();

        final List<Imp> imps = singletonList(givenImp("i1", true));

        final List<BidderResponse> bidderResponses = givenBidderResponse(firstBid, secondBid, thirdBid, fourthBid);

        // when
        final Set<Bid> winningBids = winningBidsResolver.resolveWinningBids(bidderResponses, imps, true);

        // then
        assertThat(winningBids)
                .extracting(Bid::getId)
                .containsOnly("bidId4");
    }

    private static BidderBid givenBidderBid(String bidId, String impId, String dealId, float price) {
        return BidderBid.of(
                Bid.builder().id(bidId).impid(impId).dealid(dealId).price(BigDecimal.valueOf(price)).build(),
                BidType.banner, "USD");
    }

    private static BidderSeatBid givenSeatBid(BidderBid... bidderBids) {
        return BidderSeatBid.of(Arrays.asList(bidderBids), null, null);
    }

    private static Imp givenImp(String impId, Boolean preferDeals) {
        return Imp.builder().id(impId)
                .ext(mapper.valueToTree(
                        ExtImp.of(null, null, preferDeals != null ? ExtImpTargeting.of(preferDeals) : null)))
                .build();
    }

    private List<BidderResponse> givenBidderResponse(Bid... bids) {
        return IntStream.range(0, bids.length)
                .mapToObj(i -> BidderResponse.of("bidder" + i,
                        givenSeatBid(BidderBid.of(bids[i], banner, null)), 100))
                .collect(Collectors.toList());
    }
}
