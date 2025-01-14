package org.prebid.server.floors;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.bidder.model.PriceFloorInfo;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.floors.model.PriceFloorEnforcement;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class BasicPriceFloorEnforcerTest {

    @Mock
    private BidRejectionTracker rejectionTracker;
    @Mock(strictness = LENIENT)
    private CurrencyConversionService currencyConversionService;
    @Mock(strictness = LENIENT)
    private PriceFloorAdjuster priceFloorAdjuster;
    @Mock
    private Metrics metrics;

    private BasicPriceFloorEnforcer priceFloorEnforcer;

    @BeforeEach
    public void setUp() {
        given(priceFloorAdjuster.revertAdjustmentForImp(any(), any(), any(), any())).willAnswer(invocation -> {
            final Imp argument = invocation.getArgument(0);
            return Price.of(argument.getBidfloorcur(), argument.getBidfloor());
        });
        priceFloorEnforcer = new BasicPriceFloorEnforcer(currencyConversionService, priceFloorAdjuster, metrics);
    }

    @Test
    public void shouldNotEnforceIfAccountHasDisabledPriceFloors() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(givenBidRequest(identity()), null);

        final Account account = givenAccount(accountFloors -> accountFloors.enabled(false));

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(
                null, auctionParticipation, account, rejectionTracker);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfRequestFloorsDisabled() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                givenBidRequest(request -> request.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .floors(PriceFloorRules.builder().enabled(false).build())
                        .build()))),
                givenBidderSeatBid(identity()));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(
                null, auctionParticipation, account, rejectionTracker);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfRequestFloorsSkipped() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                givenBidRequest(request -> request.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .floors(PriceFloorRules.builder().skipped(true).build())
                        .build()))),
                givenBidderSeatBid(identity()));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(
                null, auctionParticipation, account, rejectionTracker);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfRequestDoesNotEnforceFloors() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                givenBidRequest(identity(), enforcement -> enforcement.enforcePbs(false)),
                givenBidderSeatBid(identity()));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(
                null, auctionParticipation, account, rejectionTracker);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfBidderRespondsNoBids() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                givenBidRequest(identity()),
                givenBidderSeatBid());

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(
                null, auctionParticipation, account, rejectionTracker);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfBidderRespondsBidsWithDealsButRequestDoesNotEnforceFloorsForDeals() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(givenImps(identity())),
                enforcement -> enforcement.floorDeals(false));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                bidRequest,
                givenBidderSeatBid(bid -> bid.dealid("dealId")));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(
                null, auctionParticipation, account, rejectionTracker);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfBidderRespondsBidsWithDealsButAccountDoesNotEnforceFloorsForDeals() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(givenImps(identity())),
                enforcement -> enforcement.floorDeals(true));
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                bidRequest,
                givenBidderSeatBid(bid -> bid.dealid("dealId")));

        final Account account = givenAccount(accountFloors -> accountFloors.enforceDealFloors(false));

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(
                null, auctionParticipation, account, rejectionTracker);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfRequestImpHasNoBidFloorDefined() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request.imp(givenImps(identity())));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                bidRequest,
                givenBidderSeatBid(identity()));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(

                bidRequest, auctionParticipation, account, rejectionTracker);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfRequestEnforceFloorsRateIsZero() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(givenImps(identity())),
                enforcement -> enforcement.enforceRate(0));
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                bidRequest,
                givenBidderSeatBid(identity()));

        final Account account = givenAccount(accountFloors -> accountFloors.enforceFloorsRate(100));

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(
                null, auctionParticipation, account, rejectionTracker);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfRequestEnforceFloorsRateIsLessThenZero() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(givenImps(identity())),
                enforcement -> enforcement.enforceRate(-1));
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                bidRequest,
                givenBidderSeatBid(identity()));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(
                null, auctionParticipation, account, rejectionTracker);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfAccountEnforceFloorsRateIsLessThenZero() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                givenBidRequest(request -> request.imp(givenImps(identity()))),
                givenBidderSeatBid(identity()));

        final Account account = givenAccount(accountFloors -> accountFloors.enforceFloorsRate(-1));

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(
                null, auctionParticipation, account, rejectionTracker);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfRequestEnforceFloorsRateIsGreaterThen100() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(givenImps(identity())),
                enforcement -> enforcement.enforceRate(101));
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                bidRequest,
                givenBidderSeatBid(identity()));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(
                null, auctionParticipation, account, rejectionTracker);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldNotEnforceIfAccountEnforceFloorsRateIsGreaterThen100() {
        // given
        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                givenBidRequest(request -> request.imp(givenImps(identity()))),
                givenBidderSeatBid(identity()));

        final Account account = givenAccount(accountFloors -> accountFloors.enforceFloorsRate(101));

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(
                null, auctionParticipation, account, rejectionTracker);

        // then
        assertSame(result, auctionParticipation);
    }

    @Test
    public void shouldRejectBidsHavingPriceBelowFloor() {
        // given
        final BidRequest bidRequest = givenBidRequest(request ->
                request.imp(givenImps(imp -> imp.id("impId1"), imp -> imp.id("impId2"))).cur(singletonList("USD")));

        final BidRequest bidderBidRequest = givenBidRequest(request ->
                request.imp(givenImps(
                        imp -> imp.id("impId1").bidfloor(new BigDecimal("0.11")).bidfloorcur("USD"),
                        imp -> imp.id("impId2").bidfloor(new BigDecimal("0.22")).bidfloorcur("USD"))));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                bidderBidRequest,
                givenBidderSeatBid(
                        bid -> bid.id("bidId1").impid("impId1").price(BigDecimal.ONE),
                        bid -> bid.id("bidId2").impid("impId2").price(BigDecimal.TEN)));

        final Account account = givenAccount(identity());

        final String givenBidder = auctionParticipation.getBidderResponse().getBidder();
        final List<Imp> givenImps = bidderBidRequest.getImp();

        given(priceFloorAdjuster.revertAdjustmentForImp(givenImps.get(0), givenBidder, bidRequest, account))
                .willReturn(Price.of("USD", new BigDecimal("1.1")));
        given(priceFloorAdjuster.revertAdjustmentForImp(givenImps.get(1), givenBidder, bidRequest, account))
                .willReturn(Price.of("USD", new BigDecimal("2.2")));

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(
                bidRequest, auctionParticipation, account, rejectionTracker);

        // then
        verify(priceFloorAdjuster, times(2)).revertAdjustmentForImp(any(), any(), any(), any());

        final BidderBid rejectedBid = BidderBid.of(
                Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.ONE).build(), null, null);
        verify(rejectionTracker).rejectBid(rejectedBid, BidRejectionReason.RESPONSE_REJECTED_BELOW_FLOOR);
        assertThat(singleton(result))
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids, BidderSeatBid::getWarnings)
                .containsExactly(
                        singletonList(BidderBid.of(
                                Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.TEN).build(), null, null)),
                        singletonList(BidderError.of(
                                "Bid with id 'bidId1' was rejected by floor enforcement: "
                                + "price 1 is below the floor 1.1",
                                BidderError.Type.rejected_ipf,
                                singleton("impId1"))));
    }

    @Test
    public void shouldRejectBidsHavingPriceBelowFloorAndRequestEnforceFloorsRateIs100() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.imp(givenImps(imp -> imp.bidfloor(BigDecimal.TEN))),
                enforcement -> enforcement.enforceRate(100));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                bidRequest,
                givenBidderSeatBid(bid -> bid.price(BigDecimal.ONE)));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(
                bidRequest, auctionParticipation, account, rejectionTracker);

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
                bidRequest.toBuilder().imp(givenImps(imp -> imp.bidfloor(BigDecimal.ONE))).build(),
                givenBidderSeatBid(bid -> bid.price(BigDecimal.ONE)));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(
                bidRequest, auctionParticipation, account, rejectionTracker);

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
                bidRequest.toBuilder().imp(givenImps(imp -> imp.bidfloor(BigDecimal.ONE))).build(),
                givenBidderSeatBid(bid -> bid.price(BigDecimal.TEN)));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(
                bidRequest, auctionParticipation, account, rejectionTracker);

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
        final BidRequest bidderRequest = givenBidRequest(
                request -> request.imp(givenImps(imp -> imp.bidfloor(BigDecimal.ZERO))),
                enforcement -> enforcement.enforceRate(100));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                bidderRequest,
                givenBidderSeatBid(
                        PriceFloorInfo.of(BigDecimal.TEN, null),
                        bid -> bid.price(BigDecimal.ONE)));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(
                bidRequest, auctionParticipation, account, rejectionTracker);

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
                bidRequest.toBuilder().imp(givenImps(imp -> imp.bidfloor(BigDecimal.ZERO))).build(),
                givenBidderSeatBid(
                        PriceFloorInfo.of(BigDecimal.ONE, null),
                        bid -> bid.price(BigDecimal.TEN)));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(
                bidRequest, auctionParticipation, account, rejectionTracker);

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

        final BidRequest bidRequest = givenBidRequest(request -> request
                .imp(givenImps(imp -> imp.bidfloorcur("USD").bidfloor(BigDecimal.ONE)))
                .cur(singletonList("EUR")));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                bidRequest,
                givenBidderSeatBid(bid -> bid.price(BigDecimal.TEN)));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(
                bidRequest, auctionParticipation, account, rejectionTracker);

        // then
        verify(currencyConversionService)
                .convertCurrency(eq(BigDecimal.ONE), eq(bidRequest), eq("USD"), eq("EUR"));
        verify(metrics).updatePriceFloorGeneralAlertsMetric(MetricName.err);
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

        final BidRequest bidRequest = givenBidRequest(request -> request
                .imp(givenImps(imp -> imp.bidfloorcur("USD").bidfloor(BigDecimal.ONE)))
                .cur(singletonList("EUR")));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                bidRequest,
                givenBidderSeatBid(bid -> bid.price(BigDecimal.TEN)));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(
                bidRequest, auctionParticipation, account, rejectionTracker);

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

        final BidRequest bidRequest = givenBidRequest(request -> request
                .imp(givenImps(imp -> imp.bidfloor(BigDecimal.ONE).bidfloorcur("JPY")))
                .cur(singletonList("EUR")));

        final BidRequest bidderRequest = givenBidRequest(request -> bidRequest.toBuilder().cur(singletonList("USD")));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                bidderRequest,
                givenBidderSeatBid(bid -> bid.price(BigDecimal.TEN)));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(
                bidRequest, auctionParticipation, account, rejectionTracker);

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

        final BidRequest bidRequest = givenBidRequest(request -> request.cur(singletonList("EUR")));
        final BidRequest bidderRequest = givenBidRequest(
                request -> request.cur(singletonList("USD")).imp(givenImps(imp -> imp.bidfloor(BigDecimal.ZERO))));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                bidderRequest,
                givenBidderSeatBid(
                        PriceFloorInfo.of(BigDecimal.ONE, null),
                        bid -> bid.price(BigDecimal.TEN)));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(
                bidRequest, auctionParticipation, account, rejectionTracker);

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

        final BidRequest bidRequest = givenBidRequest(request -> request.cur(singletonList("EUR")));

        final BidRequest bidderRequest = givenBidRequest(
                request -> request.cur(singletonList("USD")).imp(givenImps(imp -> imp.bidfloor(BigDecimal.ZERO))));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(
                bidderRequest,
                givenBidderSeatBid(
                        PriceFloorInfo.of(BigDecimal.ONE, "JPY"),
                        bid -> bid.price(BigDecimal.TEN)));

        final Account account = givenAccount(identity());

        // when
        final AuctionParticipation result = priceFloorEnforcer.enforce(
                bidRequest, auctionParticipation, account, rejectionTracker);

        // then
        verify(currencyConversionService)
                .convertCurrency(eq(BigDecimal.ONE), eq(bidRequest), eq("JPY"), eq("EUR"));

        assertThat(singleton(result))
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .hasSize(1);
    }

    private static AuctionParticipation givenAuctionParticipation(BidRequest bidRequest, BidderSeatBid bidderSeatBid) {
        return AuctionParticipation.builder()
                .bidderRequest(BidderRequest.builder()
                        .bidder("bidder1")
                        .bidRequest(bidRequest)
                        .build())
                .bidderResponse(BidderResponse.of("bidder", bidderSeatBid, 0))
                .build();
    }

    private static Account givenAccount(
            UnaryOperator<AccountPriceFloorsConfig.AccountPriceFloorsConfigBuilder> accountFloorsCustomizer) {

        return Account.builder()
                .auction(AccountAuctionConfig.builder()
                        .priceFloors(accountFloorsCustomizer.apply(AccountPriceFloorsConfig.builder()
                                        .enabled(true))
                                .build())
                        .build())
                .build();
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> requestCustomizer,
            UnaryOperator<PriceFloorEnforcement.PriceFloorEnforcementBuilder> enforcementCustomizer) {

        return requestCustomizer.apply(BidRequest.builder()
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .floors(PriceFloorRules.builder()
                                        .enforcement(givenEnforcement(enforcementCustomizer))
                                        .build())
                                .build())))
                .build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> requestCustomizer) {
        return givenBidRequest(requestCustomizer, identity());
    }

    private static PriceFloorEnforcement givenEnforcement(
            UnaryOperator<PriceFloorEnforcement.PriceFloorEnforcementBuilder> enforcementCustomizer) {

        return enforcementCustomizer.apply(PriceFloorEnforcement.builder().enforcePbs(true)).build();
    }

    @SafeVarargs
    private static List<Imp> givenImps(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        if (impCustomizers == null) {
            return emptyList();
        }
        return Arrays.stream(impCustomizers)
                .map(impCustomizer -> impCustomizer.apply(Imp.builder().id("impId")).build())
                .toList();
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
                .map(bid -> BidderBid.builder().bid(bid).priceFloorInfo(priceFloorInfo).build())
                .toList();
        return BidderSeatBid.of(bidderBids);
    }
}
