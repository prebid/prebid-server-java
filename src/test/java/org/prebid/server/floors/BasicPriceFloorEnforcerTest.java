package org.prebid.server.floors;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidFloors;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertSame;

public class BasicPriceFloorEnforcerTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private BasicPriceFloorEnforcer enforcer;

    @Before
    public void setUp() {
        enforcer = new BasicPriceFloorEnforcer();
    }

    @Test
    public void shouldNotEnforceIfAccountHasDisabledPriceFloors() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(identity(), null);
        final Account account = givenAccount(builder -> builder.enabled(false));

        // when
        final AuctionParticipation result = enforcer.enforce(auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfExtPrebidFloorsIsNotDefinedInRequest() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(identity(), null);
        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = enforcer.enforce(auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfBidderRespondsNoBids() {
        // given
        final ExtRequestPrebidFloors floors = givenExtRequestPrebidFloors(identity());
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(identity(), floors);
        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = enforcer.enforce(auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfBidderRespondsBidsWithDealsButRequestDoesNotEnforceDealsForFloors() {
        // given
        final ExtRequestPrebidFloors floors = givenExtRequestPrebidFloors(builder -> builder.enforceDeals(false));
        final BidderSeatBid bidderSeatBid = givenBidderSeatBid(List.of(
                BidderBid.of(Bid.builder().impid("impId").dealid("dealId").build(), null, null)));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(List.of(Imp.builder().id("impId").build())),
                floors,
                bidderSeatBid);
        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = enforcer.enforce(auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfBidderRespondsBidsWithDealsButAccountDoesNotEnforceDealsForFloors() {
        // given
        final ExtRequestPrebidFloors floors = givenExtRequestPrebidFloors(builder -> builder.enforceDeals(true));
        final BidderSeatBid bidderSeatBid = givenBidderSeatBid(List.of(
                BidderBid.of(Bid.builder().impid("impId").dealid("dealId").build(), null, null)));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(List.of(Imp.builder().id("impId").build())),
                floors,
                bidderSeatBid);
        final Account account = givenAccount(builder -> builder.enforceDealFloors(false));

        // when
        final AuctionParticipation result = enforcer.enforce(auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfRequestImpHasNoBidFloorDefined() {
        // given
        final ExtRequestPrebidFloors floors = givenExtRequestPrebidFloors(identity());
        final BidderSeatBid bidderSeatBid = givenBidderSeatBid(List.of(
                BidderBid.of(Bid.builder().impid("impId").build(), null, null)));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(List.of(Imp.builder().id("impId").build())),
                floors,
                bidderSeatBid);
        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = enforcer.enforce(auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfRequestEnforceFloorsRateIsZero() {
        // given
        final ExtRequestPrebidFloors floors = givenExtRequestPrebidFloors(builder -> builder.enforceRate(0));
        final BidderSeatBid bidderSeatBid = givenBidderSeatBid(List.of(
                BidderBid.of(Bid.builder().impid("impId").build(), null, null)));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(List.of(Imp.builder().id("impId").build())),
                floors,
                bidderSeatBid);
        final Account account = givenAccount(builder -> builder.enforceFloorsRate(100));

        // when
        final AuctionParticipation result = enforcer.enforce(auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfAccountEnforceFloorsRateIsZero() {
        // given
        final ExtRequestPrebidFloors floors = givenExtRequestPrebidFloors(identity());
        final BidderSeatBid bidderSeatBid = givenBidderSeatBid(List.of(
                BidderBid.of(Bid.builder().impid("impId").build(), null, null)));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(List.of(Imp.builder().id("impId").build())),
                floors,
                bidderSeatBid);
        final Account account = givenAccount(builder -> builder.enforceFloorsRate(0));

        // when
        final AuctionParticipation result = enforcer.enforce(auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldRejectBidsHavingPriceBelowFloor() {
        // given
        final ExtRequestPrebidFloors floors = givenExtRequestPrebidFloors(identity());
        final BidderSeatBid bidderSeatBid = givenBidderSeatBid(List.of(
                BidderBid.of(Bid.builder().impid("impId").price(BigDecimal.ONE).build(), null, null)));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(List.of(Imp.builder().id("impId").bidfloor(BigDecimal.TEN).build())),
                floors,
                bidderSeatBid);
        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = enforcer.enforce(auctionParticipation, account);

        // then
        assertThat(singleton(result))
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids, BidderSeatBid::getErrors)
                .containsExactly(
                        emptyList(),
                        List.of(BidderError.of("Bid with id 'null' was rejected: price 1 is below the floor 10",
                                BidderError.Type.generic)));
    }

    @Test
    public void shouldRejectBidsHavingPriceBelowFloorAndRequestEnforceFloorsRateIs100() {
        // given
        final ExtRequestPrebidFloors floors = givenExtRequestPrebidFloors(builder -> builder.enforceRate(100));
        final BidderSeatBid bidderSeatBid = givenBidderSeatBid(List.of(
                BidderBid.of(Bid.builder().impid("impId").price(BigDecimal.ONE).build(), null, null)));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(List.of(Imp.builder().id("impId").bidfloor(BigDecimal.TEN).build())),
                floors,
                bidderSeatBid);
        final Account account = givenAccount(builder -> builder.enforceFloorsRate(0));

        // when
        final AuctionParticipation result = enforcer.enforce(auctionParticipation, account);

        // then
        assertThat(singleton(result))
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .isEmpty();
    }

    @Test
    public void shouldRejectBidsHavingPriceBelowFloorAndAccountEnforceFloorsRateIs100() {
        // given
        final ExtRequestPrebidFloors floors = givenExtRequestPrebidFloors(identity());
        final BidderSeatBid bidderSeatBid = givenBidderSeatBid(List.of(
                BidderBid.of(Bid.builder().impid("impId").price(BigDecimal.ONE).build(), null, null)));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(List.of(Imp.builder().id("impId").bidfloor(BigDecimal.TEN).build())),
                floors,
                bidderSeatBid);
        final Account account = givenAccount(builder -> builder.enforceFloorsRate(100));

        // when
        final AuctionParticipation result = enforcer.enforce(auctionParticipation, account);

        // then
        assertThat(singleton(result))
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .isEmpty();
    }

    @Test
    public void shouldRemainBidsHavingPriceEqualToFloor() {
        // given
        final ExtRequestPrebidFloors floors = givenExtRequestPrebidFloors(identity());
        final BidderSeatBid bidderSeatBid = givenBidderSeatBid(List.of(
                BidderBid.of(Bid.builder().impid("impId").price(BigDecimal.ONE).build(), null, null)));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(List.of(Imp.builder().id("impId").bidfloor(BigDecimal.ONE).build())),
                floors,
                bidderSeatBid);
        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = enforcer.enforce(auctionParticipation, account);

        // then
        assertThat(singleton(result))
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .hasSize(1);
    }

    @Test
    public void shouldRemainBidsHavingPriceGreaterThenFloor() {
        // given
        final ExtRequestPrebidFloors floors = givenExtRequestPrebidFloors(identity());
        final BidderSeatBid bidderSeatBid = givenBidderSeatBid(List.of(
                BidderBid.of(Bid.builder().impid("impId").price(BigDecimal.TEN).build(), null, null)));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(List.of(Imp.builder().id("impId").bidfloor(BigDecimal.ONE).build())),
                floors,
                bidderSeatBid);
        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = enforcer.enforce(auctionParticipation, account);

        // then
        assertThat(singleton(result))
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .hasSize(1);
    }

    private static Account givenAccount(
            UnaryOperator<AccountPriceFloorsConfig.AccountPriceFloorsConfigBuilder> customizer) {

        return Account.builder()
                .auction(AccountAuctionConfig.builder()
                        .priceFloors(customizer.apply(AccountPriceFloorsConfig.builder()
                                        .enabled(true))
                                .build())
                        .build())
                .build();
    }

    private static AuctionParticipation givenAuctionParticipation(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            ExtRequestPrebidFloors extRequestPrebidFloors) {

        return givenAuctionParticipation(bidRequestCustomizer, extRequestPrebidFloors, BidderSeatBid.empty());
    }

    private static AuctionParticipation givenAuctionParticipation(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            ExtRequestPrebidFloors extRequestPrebidFloors,
            BidderSeatBid bidderSeatBid) {

        return AuctionParticipation.builder()
                .bidderRequest(BidderRequest.of(
                        "bidder",
                        null,
                        bidRequestCustomizer.apply(BidRequest.builder()
                                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                                .floors(extRequestPrebidFloors)
                                                .build())))
                                .build()))
                .bidderResponse(BidderResponse.of("bidder", bidderSeatBid, 0))
                .build();
    }

    private static ExtRequestPrebidFloors givenExtRequestPrebidFloors(
            UnaryOperator<ExtRequestPrebidFloors.ExtRequestPrebidFloorsBuilder> customizer) {

        return customizer.apply(ExtRequestPrebidFloors.builder().enforcePbs(true)).build();
    }

    private BidderSeatBid givenBidderSeatBid(List<BidderBid> bids) {
        return BidderSeatBid.of(bids, emptyList(), emptyList());
    }
}
