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
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidFloorsEnforcement;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;

import java.math.BigDecimal;
import java.util.ArrayList;
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
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                identity(),
                null,
                null);

        final Account account = givenAccount(builder -> builder.enabled(false));

        // when
        final AuctionParticipation result = enforcer.enforce(auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfRequestDoesNotEnforceFloors() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                identity(),
                ExtRequestPrebidFloorsEnforcement.of(null, null, false),
                givenBidderSeatBid(identity()));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = enforcer.enforce(auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfBidderRespondsNoBids() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                identity(),
                ExtRequestPrebidFloorsEnforcement.of(null, null, true),
                givenBidderSeatBid());

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = enforcer.enforce(auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfBidderRespondsBidsWithDealsButRequestDoesNotEnforceDealsForFloors() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(givenImps(identity())),
                ExtRequestPrebidFloorsEnforcement.of(null, false, true),
                givenBidderSeatBid(builder -> builder.dealid("dealId")));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = enforcer.enforce(auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfBidderRespondsBidsWithDealsButAccountDoesNotEnforceDealsForFloors() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(givenImps(identity())),
                ExtRequestPrebidFloorsEnforcement.of(null, true, true),
                givenBidderSeatBid(builder -> builder.dealid("dealId")));

        final Account account = givenAccount(builder -> builder.enforceDealFloors(false));

        // when
        final AuctionParticipation result = enforcer.enforce(auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfRequestImpHasNoBidFloorDefined() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(givenImps(identity())),
                ExtRequestPrebidFloorsEnforcement.of(null, null, true),
                givenBidderSeatBid(identity()));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = enforcer.enforce(auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfRequestEnforceFloorsRateIsZero() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(givenImps(identity())),
                ExtRequestPrebidFloorsEnforcement.of(0, null, true),
                givenBidderSeatBid(identity()));

        final Account account = givenAccount(builder -> builder.enforceFloorsRate(100));

        // when
        final AuctionParticipation result = enforcer.enforce(auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfAccountEnforceFloorsRateIsZero() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(givenImps(identity())),
                ExtRequestPrebidFloorsEnforcement.of(100, null, true),
                givenBidderSeatBid(identity()));

        final Account account = givenAccount(builder -> builder.enforceFloorsRate(0));

        // when
        final AuctionParticipation result = enforcer.enforce(auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfEnforceFloorsRateIsLessThenZero() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(givenImps(identity())),
                ExtRequestPrebidFloorsEnforcement.of(-1, null, true),
                givenBidderSeatBid(identity()));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = enforcer.enforce(auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfEnforceFloorsRateIsGreaterThen100() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(givenImps(identity())),
                ExtRequestPrebidFloorsEnforcement.of(101, null, true),
                givenBidderSeatBid(identity()));
        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = enforcer.enforce(auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldRejectBidsHavingPriceBelowFloor() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(givenImps(impBuilder -> impBuilder.bidfloor(BigDecimal.ONE))),
                ExtRequestPrebidFloorsEnforcement.of(null, null, true),
                givenBidderSeatBid(
                        builder -> builder.id("bidId1").price(BigDecimal.ZERO),
                        builder -> builder.id("bidId2").price(BigDecimal.TEN)));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = enforcer.enforce(auctionParticipation, account);

        // then
        assertThat(singleton(result))
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids, BidderSeatBid::getErrors)
                .containsExactly(
                        List.of(BidderBid.of(
                                Bid.builder().id("bidId2").impid("impId").price(BigDecimal.TEN).build(), null, null)),
                        List.of(BidderError.of(
                                "Bid with id 'bidId1' was rejected: price 0 is below the floor 1",
                                BidderError.Type.generic)));
    }

    @Test
    public void shouldRejectBidsHavingPriceBelowFloorAndRequestEnforceFloorsRateIs100() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(givenImps(impBuilder -> impBuilder.bidfloor(BigDecimal.TEN))),
                ExtRequestPrebidFloorsEnforcement.of(100, null, true),
                givenBidderSeatBid(builder -> builder.price(BigDecimal.ONE)));

        final Account account = givenAccount(identity());

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
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(givenImps(impBuilder -> impBuilder.bidfloor(BigDecimal.TEN))),
                ExtRequestPrebidFloorsEnforcement.of(null, null, true),
                givenBidderSeatBid(builder -> builder.price(BigDecimal.ONE)));

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
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(givenImps(impBuilder -> impBuilder.bidfloor(BigDecimal.ONE))),
                ExtRequestPrebidFloorsEnforcement.of(null, null, true),
                givenBidderSeatBid(builder -> builder.price(BigDecimal.ONE)));
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
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(givenImps(impBuilder -> impBuilder.bidfloor(BigDecimal.ONE))),
                ExtRequestPrebidFloorsEnforcement.of(null, null, true),
                givenBidderSeatBid(builder -> builder.price(BigDecimal.TEN)));

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
            ExtRequestPrebidFloorsEnforcement floorsEnforcement,
            BidderSeatBid bidderSeatBid) {

        return AuctionParticipation.builder()
                .bidderRequest(BidderRequest.of(
                        "bidder",
                        null,
                        bidRequestCustomizer.apply(BidRequest.builder()
                                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                                .floors(ExtRequestPrebidFloors.builder()
                                                        .enforcement(floorsEnforcement)
                                                        .build())
                                                .build())))
                                .build()))
                .bidderResponse(BidderResponse.of("bidder", bidderSeatBid, 0))
                .build();
    }

    @SafeVarargs
    private static List<Imp> givenImps(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        if (impCustomizers == null) {
            return emptyList();
        }
        final List<Imp> imps = new ArrayList<>();
        for (UnaryOperator<Imp.ImpBuilder> impCustomizer : impCustomizers) {
            imps.add(impCustomizer.apply(Imp.builder().id("impId")).build());
        }
        return imps;
    }

    @SafeVarargs
    private BidderSeatBid givenBidderSeatBid(UnaryOperator<Bid.BidBuilder>... bidCustomizers) {
        if (bidCustomizers == null) {
            return BidderSeatBid.empty();
        }
        final List<BidderBid> bidderBids = new ArrayList<>();
        for (UnaryOperator<Bid.BidBuilder> bidCustomizer : bidCustomizers) {
            bidderBids.add(BidderBid.of(bidCustomizer.apply(Bid.builder().impid("impId")).build(), null, null));
        }
        return BidderSeatBid.of(bidderBids, emptyList(), emptyList());
    }
}
