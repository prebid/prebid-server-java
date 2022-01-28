package org.prebid.server.floors;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.bidder.model.PriceFloorInfo;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.floors.model.PriceFloorEnforcement;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class BasicPriceFloorEnforcerTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private CurrencyConversionService currencyConversionService;

    private BasicPriceFloorEnforcer priceFloorEnforcer;

    @Before
    public void setUp() {
        priceFloorEnforcer = new BasicPriceFloorEnforcer(currencyConversionService);
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
        final AuctionParticipation result = priceFloorEnforcer.enforce(null, auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfRequestDoesNotEnforceFloors() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                identity(),
                PriceFloorEnforcement.builder().enforcePbs(false).build(),
                givenBidderSeatBid(identity()));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(null, auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfBidderRespondsNoBids() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                identity(),
                PriceFloorEnforcement.builder().enforcePbs(true).build(),
                givenBidderSeatBid());

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(null, auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfBidderRespondsBidsWithDealsButRequestDoesNotEnforceDealsForFloors() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(givenImps(identity())),
                PriceFloorEnforcement.builder().enforcePbs(true).floorDeals(false).build(),
                givenBidderSeatBid(builder -> builder.dealid("dealId")));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(null, auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfBidderRespondsBidsWithDealsButAccountDoesNotEnforceDealsForFloors() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(givenImps(identity())),
                PriceFloorEnforcement.builder().enforcePbs(true).floorDeals(true).build(),
                givenBidderSeatBid(builder -> builder.dealid("dealId")));

        final Account account = givenAccount(builder -> builder.enforceDealFloors(false));

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(null, auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfRequestImpHasNoBidFloorDefined() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(givenImps(identity())),
                PriceFloorEnforcement.builder().enforcePbs(true).build(),
                givenBidderSeatBid(identity()));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(bidRequest, auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfRequestEnforceFloorsRateIsZero() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(givenImps(identity())),
                PriceFloorEnforcement.builder().enforcePbs(true).enforceRate(0).build(),
                givenBidderSeatBid(identity()));

        final Account account = givenAccount(builder -> builder.enforceFloorsRate(100));

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(null, auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfEnforceFloorsRateIsLessThenZero() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(givenImps(identity())),
                PriceFloorEnforcement.builder().enforcePbs(true).enforceRate(-1).build(),
                givenBidderSeatBid(identity()));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(null, auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfEnforceFloorsRateIsGreaterThen100() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(givenImps(identity())),
                PriceFloorEnforcement.builder().enforcePbs(true).enforceRate(101).build(),
                givenBidderSeatBid(identity()));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(null, auctionParticipation, account);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldRejectBidsHavingPriceBelowFloor() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(givenImps(impBuilder -> impBuilder.bidfloor(BigDecimal.ONE))),
                PriceFloorEnforcement.builder().enforcePbs(true).build(),
                givenBidderSeatBid(
                        builder -> builder.id("bidId1").price(BigDecimal.ZERO),
                        builder -> builder.id("bidId2").price(BigDecimal.TEN)));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(bidRequest, auctionParticipation, account);

        // then
        assertThat(singleton(result))
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids, BidderSeatBid::getErrors)
                .containsExactly(
                        singletonList(BidderBid.of(
                                Bid.builder().id("bidId2").impid("impId").price(BigDecimal.TEN).build(), null, null)),
                        singletonList(BidderError.of("Bid with id 'bidId1' was rejected by floor enforcement: "
                                + "price 0 is below the floor 1", BidderError.Type.generic)));
    }

    @Test
    public void shouldRejectBidsHavingPriceBelowFloorAndRequestEnforceFloorsRateIs100() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(givenImps(impBuilder -> impBuilder.bidfloor(BigDecimal.TEN))),
                PriceFloorEnforcement.builder().enforcePbs(true).enforceRate(100).build(),
                givenBidderSeatBid(builder -> builder.price(BigDecimal.ONE)));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(bidRequest, auctionParticipation, account);

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
        final BidRequest bidRequest = givenBidRequest(identity());

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(givenImps(impBuilder -> impBuilder.bidfloor(BigDecimal.ONE))),
                PriceFloorEnforcement.builder().enforcePbs(true).build(),
                givenBidderSeatBid(builder -> builder.price(BigDecimal.ONE)));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(bidRequest, auctionParticipation, account);

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
        final BidRequest bidRequest = givenBidRequest(identity());

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(givenImps(impBuilder -> impBuilder.bidfloor(BigDecimal.ONE))),
                PriceFloorEnforcement.builder().enforcePbs(true).build(),
                givenBidderSeatBid(builder -> builder.price(BigDecimal.TEN)));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(bidRequest, auctionParticipation, account);

        // then
        assertThat(singleton(result))
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .hasSize(1);
    }

    @Test
    public void shouldRejectBidsHavingPriceBelowCustomBidderFloor() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(givenImps(impBuilder -> impBuilder.bidfloor(BigDecimal.ZERO))),
                PriceFloorEnforcement.builder().enforcePbs(true).enforceRate(100).build(),
                givenBidderSeatBid(
                        PriceFloorInfo.of(BigDecimal.TEN, null),
                        builder -> builder.price(BigDecimal.ONE)));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(bidRequest, auctionParticipation, account);

        // then
        assertThat(singleton(result))
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .isEmpty();
    }

    @Test
    public void shouldRemainBidsHavingPriceGreaterThenCustomBidderFloor() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                builder -> builder.imp(givenImps(impBuilder -> impBuilder.bidfloor(BigDecimal.ZERO))),
                PriceFloorEnforcement.builder().enforcePbs(true).build(),
                givenBidderSeatBid(
                        PriceFloorInfo.of(BigDecimal.ONE, null),
                        builder -> builder.price(BigDecimal.TEN)));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(bidRequest, auctionParticipation, account);

        // then
        assertThat(singleton(result))
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .hasSize(1);
    }

    @Test
    public void shouldRemainBidsEvenCurrencyConversionForFloorIsFailed() {
        // given
        given(currencyConversionService.convertCurrency(any(), any(), any(), any()))
                .willThrow(new PreBidException("error"));

        final BidRequest bidRequest = givenBidRequest(builder -> builder.cur(singletonList("EUR")));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(builder -> builder
                        .cur(singletonList("USD"))
                        .imp(givenImps(impBuilder -> impBuilder.bidfloor(BigDecimal.ONE))),
                PriceFloorEnforcement.builder().enforcePbs(true).build(),
                givenBidderSeatBid(builder -> builder.price(BigDecimal.TEN)));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(bidRequest, auctionParticipation, account);

        // then
        verify(currencyConversionService)
                .convertCurrency(eq(BigDecimal.ONE), eq(bidRequest), eq("USD"), eq("EUR"));

        assertThat(singleton(result))
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids, BidderSeatBid::getErrors)
                .containsExactly(
                        singletonList(BidderBid.of(
                                Bid.builder().impid("impId").price(BigDecimal.TEN).build(), null, null)),
                        singletonList(BidderError.badServerResponse("Price floors enforcement failed: error")));
    }

    @Test
    public void shouldRemainBidsHavingPriceGreaterThenConvertedFloor() {
        // given
        given(currencyConversionService.convertCurrency(any(), any(), any(), any())).willReturn(BigDecimal.TEN);

        final BidRequest bidRequest = givenBidRequest(builder -> builder.cur(singletonList("EUR")));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(builder -> builder
                        .cur(singletonList("USD"))
                        .imp(givenImps(impBuilder -> impBuilder.bidfloor(BigDecimal.ONE))),
                PriceFloorEnforcement.builder().enforcePbs(true).build(),
                givenBidderSeatBid(builder -> builder.price(BigDecimal.TEN)));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(bidRequest, auctionParticipation, account);

        // then
        verify(currencyConversionService)
                .convertCurrency(eq(BigDecimal.ONE), eq(bidRequest), eq("USD"), eq("EUR"));

        assertThat(singleton(result))
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .hasSize(1);
    }

    @Test
    public void shouldRemainBidsHavingPriceGreaterThenConvertedFloorInAnotherCurrency() {
        // given
        given(currencyConversionService.convertCurrency(any(), any(), any(), any())).willReturn(BigDecimal.TEN);

        final BidRequest bidRequest = givenBidRequest(builder -> builder.cur(singletonList("EUR")));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(builder -> builder
                        .cur(singletonList("USD"))
                        .imp(givenImps(impBuilder -> impBuilder.bidfloor(BigDecimal.ONE).bidfloorcur("JPY"))),
                PriceFloorEnforcement.builder().enforcePbs(true).build(),
                givenBidderSeatBid(builder -> builder.price(BigDecimal.TEN)));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(bidRequest, auctionParticipation, account);

        // then
        verify(currencyConversionService)
                .convertCurrency(eq(BigDecimal.ONE), eq(bidRequest), eq("JPY"), eq("EUR"));

        assertThat(singleton(result))
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .hasSize(1);
    }

    @Test
    public void shouldRemainBidsHavingPriceGreaterThenConvertedCustomBidderFloor() {
        // given
        given(currencyConversionService.convertCurrency(any(), any(), any(), any())).willReturn(BigDecimal.TEN);

        final BidRequest bidRequest = givenBidRequest(builder -> builder.cur(singletonList("EUR")));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(builder -> builder
                        .cur(singletonList("USD"))
                        .imp(givenImps(impBuilder -> impBuilder.bidfloor(BigDecimal.ZERO))),
                PriceFloorEnforcement.builder().enforcePbs(true).build(),
                givenBidderSeatBid(
                        PriceFloorInfo.of(BigDecimal.ONE, null),
                        builder -> builder.price(BigDecimal.TEN)));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(bidRequest, auctionParticipation, account);

        // then
        verify(currencyConversionService)
                .convertCurrency(eq(BigDecimal.ONE), eq(bidRequest), eq("USD"), eq("EUR"));

        assertThat(singleton(result))
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .hasSize(1);
    }

    @Test
    public void shouldRemainBidsHavingPriceGreaterThenConvertedCustomBidderFloorInAnotherCurrency() {
        // given
        given(currencyConversionService.convertCurrency(any(), any(), any(), any())).willReturn(BigDecimal.TEN);

        final BidRequest bidRequest = givenBidRequest(builder -> builder.cur(singletonList("EUR")));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(builder -> builder
                        .cur(singletonList("USD"))
                        .imp(givenImps(impBuilder -> impBuilder.bidfloor(BigDecimal.ZERO))),
                PriceFloorEnforcement.builder().enforcePbs(true).build(),
                givenBidderSeatBid(
                        PriceFloorInfo.of(BigDecimal.ONE, "JPY"),
                        builder -> builder.price(BigDecimal.TEN)));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(bidRequest, auctionParticipation, account);

        // then
        verify(currencyConversionService)
                .convertCurrency(eq(BigDecimal.ONE), eq(bidRequest), eq("JPY"), eq("EUR"));

        assertThat(singleton(result))
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .hasSize(1);
    }

    private static AuctionParticipation givenAuctionParticipation(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            PriceFloorEnforcement floorsEnforcement,
            BidderSeatBid bidderSeatBid) {

        return AuctionParticipation.builder()
                .bidderRequest(BidderRequest.of(
                        "bidder",
                        null,
                        bidRequestCustomizer.apply(BidRequest.builder()
                                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                                .floors(PriceFloorRules.builder()
                                                        .enforcement(floorsEnforcement)
                                                        .build())
                                                .build())))
                                .build()))
                .bidderResponse(BidderResponse.of("bidder", bidderSeatBid, 0))
                .build();
    }

    private static Account givenAccount(
            UnaryOperator<AccountPriceFloorsConfig.AccountPriceFloorsConfigBuilder> accountCustomizer) {

        return Account.builder()
                .auction(AccountAuctionConfig.builder()
                        .priceFloors(accountCustomizer.apply(AccountPriceFloorsConfig.builder()
                                        .enabled(true))
                                .build())
                        .build())
                .build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer) {
        return bidRequestCustomizer.apply(BidRequest.builder()).build();
    }

    @SafeVarargs
    private static List<Imp> givenImps(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        if (impCustomizers == null) {
            return emptyList();
        }
        return Arrays.stream(impCustomizers)
                .map(impCustomizer -> impCustomizer.apply(Imp.builder().id("impId")).build())
                .collect(Collectors.toList());
    }

    @SafeVarargs
    private static BidderSeatBid givenBidderSeatBid(UnaryOperator<Bid.BidBuilder>... bidCustomizers) {
        return givenBidderSeatBid(null, bidCustomizers);
    }

    @SafeVarargs
    private static BidderSeatBid givenBidderSeatBid(PriceFloorInfo priceFloorInfo,
                                                    UnaryOperator<Bid.BidBuilder>... bidCustomizers) {

        if (bidCustomizers == null) {
            return BidderSeatBid.empty();
        }
        final List<BidderBid> bidderBids = Arrays.stream(bidCustomizers)
                .map(bidCustomizer -> bidCustomizer.apply(Bid.builder().impid("impId")).build())
                .map(bid -> BidderBid.of(bid, null, null, null, null, priceFloorInfo))
                .collect(Collectors.toList());
        return BidderSeatBid.of(bidderBids, emptyList(), emptyList());
    }
}
