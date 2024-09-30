package org.prebid.server.auction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.adjustment.BidAdjustmentFactorResolver;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.floors.PriceFloorEnforcer;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentFactors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestCurrency;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.validation.ResponseBidValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.prebid.server.proto.openrtb.ext.response.BidType.audio;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

@ExtendWith(MockitoExtension.class)
public class BidsAdjusterTest extends VertxTest {

    @Mock(strictness = LENIENT)
    private ResponseBidValidator responseBidValidator;

    @Mock(strictness = LENIENT)
    private CurrencyConversionService currencyService;

    @Mock(strictness = LENIENT)
    private PriceFloorEnforcer priceFloorEnforcer;

    @Mock(strictness = LENIENT)
    private DsaEnforcer dsaEnforcer;

    @Mock(strictness = LENIENT)
    private BidAdjustmentFactorResolver bidAdjustmentFactorResolver;

    private BidsAdjuster target;

    @BeforeEach
    public void setUp() {
        given(responseBidValidator.validate(any(), any(), any(), any())).willReturn(ValidationResult.success());

        given(currencyService.convertCurrency(any(), any(), any(), any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(priceFloorEnforcer.enforce(any(), any(), any(), any())).willAnswer(inv -> inv.getArgument(1));
        given(dsaEnforcer.enforce(any(), any(), any())).willAnswer(inv -> inv.getArgument(1));
        given(bidAdjustmentFactorResolver.resolve(any(ImpMediaType.class), any(), any())).willReturn(null);

        givenTarget();
    }

    private void givenTarget() {
        target = new BidsAdjuster(
                responseBidValidator,
                currencyService,
                bidAdjustmentFactorResolver,
                priceFloorEnforcer,
                dsaEnforcer,
                jacksonMapper);
    }

    @Test
    public void shouldReturnBidsWithUpdatedPriceCurrencyConversion() {
        // given
        final BidderResponse bidderResponse = givenBidderResponse(
                Bid.builder().impid("impId").price(BigDecimal.valueOf(2.0)).build());
        final BidRequest bidRequest = givenBidRequest(
                singletonList(givenImp(singletonMap("bidder", 2), identity())), identity());

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        final BigDecimal updatedPrice = BigDecimal.valueOf(5.0);
        given(currencyService.convertCurrency(any(), any(), any(), any())).willReturn(updatedPrice);

        // when
        final List<AuctionParticipation> result = target
                .validateAndAdjustBids(auctionParticipations, auctionContext, null);

        // then
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(updatedPrice);
    }

    @Test
    public void shouldReturnSameBidPriceIfNoChangesAppliedToBidPrice() {
        // given
        final BidderResponse bidderResponse = givenBidderResponse(
                Bid.builder().impid("impId").price(BigDecimal.valueOf(2.0)).build());
        final BidRequest bidRequest = givenBidRequest(
                singletonList(givenImp(singletonMap("bidder", 2), identity())), identity());

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        given(currencyService.convertCurrency(any(), any(), any(), any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        final List<AuctionParticipation> result = target
                .validateAndAdjustBids(auctionParticipations, auctionContext, null);

        // then
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(BigDecimal.valueOf(2.0));
    }

    @Test
    public void shouldDropBidIfPrebidExceptionWasThrownDuringCurrencyConversion() {
        // given
        final BidderResponse bidderResponse = givenBidderResponse(
                Bid.builder().impid("impId").price(BigDecimal.valueOf(2.0)).build());
        final BidRequest bidRequest = givenBidRequest(
                singletonList(givenImp(singletonMap("bidder", 2), identity())), identity());

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        given(currencyService.convertCurrency(any(), any(), any(), any()))
                .willThrow(new PreBidException("Unable to convert bid currency CUR to desired ad server currency USD"));

        // when
        final List<AuctionParticipation> result = target
                .validateAndAdjustBids(auctionParticipations, auctionContext, null);

        // then

        final BidderError expectedError =
                BidderError.generic("Unable to convert bid currency CUR to desired ad server currency USD");
        final BidderSeatBid firstSeatBid = result.getFirst().getBidderResponse().getSeatBid();
        assertThat(firstSeatBid.getBids()).isEmpty();
        assertThat(firstSeatBid.getErrors()).containsOnly(expectedError);
    }

    @Test
    public void shouldUpdateBidPriceWithCurrencyConversionAndPriceAdjustmentFactor() {
        // given
        final BidderResponse bidderResponse = givenBidderResponse(
                Bid.builder().impid("impId").price(BigDecimal.valueOf(2.0)).build());

        final ExtRequestBidAdjustmentFactors givenAdjustments = ExtRequestBidAdjustmentFactors.builder().build();
        givenAdjustments.addFactor("bidder", BigDecimal.TEN);

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(emptyMap())
                        .bidadjustmentfactors(givenAdjustments)
                        .auctiontimestamp(1000L)
                        .build())));

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        given(bidAdjustmentFactorResolver.resolve(ImpMediaType.banner, givenAdjustments, "bidder"))
                .willReturn(BigDecimal.TEN);
        given(currencyService.convertCurrency(any(), any(), any(), any()))
                .willReturn(BigDecimal.TEN);

        // when
        final List<AuctionParticipation> result = target
                .validateAndAdjustBids(auctionParticipations, auctionContext, null);

        // then
        final BigDecimal updatedPrice = BigDecimal.valueOf(100);
        final BidderSeatBid firstSeatBid = result.getFirst().getBidderResponse().getSeatBid();
        assertThat(firstSeatBid.getBids())
                .extracting(BidderBid::getBid)
                .flatExtracting(Bid::getPrice)
                .containsOnly(updatedPrice);
        assertThat(firstSeatBid.getErrors()).isEmpty();
    }

    @Test
    public void shouldUpdatePriceForOneBidAndDropAnotherIfPrebidExceptionHappensForSecondBid() {
        // given
        final BigDecimal firstBidderPrice = BigDecimal.valueOf(2.0);
        final BigDecimal secondBidderPrice = BigDecimal.valueOf(3.0);
        final BidderResponse bidderResponse = BidderResponse.of(
                "bidder",
                BidderSeatBid.builder()
                        .bids(List.of(
                                givenBidderBid(Bid.builder().impid("impId1").price(firstBidderPrice).build(), "CUR1"),
                                givenBidderBid(Bid.builder().impid("impId2").price(secondBidderPrice).build(), "CUR2")
                        ))
                        .build(),
                1);

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                identity());

        final BigDecimal updatedPrice = BigDecimal.valueOf(10.0);
        given(currencyService.convertCurrency(any(), any(), any(), any())).willReturn(updatedPrice)
                .willThrow(
                        new PreBidException("Unable to convert bid currency CUR2 to desired ad server currency USD"));

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        // when
        final List<AuctionParticipation> result = target
                .validateAndAdjustBids(auctionParticipations, auctionContext, null);

        // then
        verify(currencyService).convertCurrency(eq(firstBidderPrice), eq(bidRequest), eq("CUR1"), any());
        verify(currencyService).convertCurrency(eq(secondBidderPrice), eq(bidRequest), eq("CUR2"), any());

        assertThat(result).hasSize(1);

        final ObjectNode expectedBidExt = mapper.createObjectNode();
        expectedBidExt.put("origbidcpm", new BigDecimal("2.0"));
        expectedBidExt.put("origbidcur", "CUR1");
        final Bid expectedBid = Bid.builder().impid("impId1").price(updatedPrice).ext(expectedBidExt).build();
        final BidderBid expectedBidderBid = BidderBid.of(expectedBid, banner, "CUR1");
        final BidderError expectedError =
                BidderError.generic("Unable to convert bid currency CUR2 to desired ad server currency USD");

        final BidderSeatBid firstSeatBid = result.getFirst().getBidderResponse().getSeatBid();
        assertThat(firstSeatBid.getBids()).containsOnly(expectedBidderBid);
        assertThat(firstSeatBid.getErrors()).containsOnly(expectedError);
    }

    @Test
    public void shouldRespondWithOneBidAndErrorWhenBidResponseContainsOneUnsupportedCurrency() {
        // given
        final BigDecimal firstBidderPrice = BigDecimal.valueOf(2.0);
        final BigDecimal secondBidderPrice = BigDecimal.valueOf(10.0);
        final BidderResponse bidderResponse = BidderResponse.of(
                "bidder",
                BidderSeatBid.builder()
                        .bids(List.of(
                                givenBidderBid(Bid.builder().impid("impId1").price(firstBidderPrice).build(), "USD"),
                                givenBidderBid(Bid.builder().impid("impId2").price(secondBidderPrice).build(), "CUR")
                        ))
                        .build(),
                1);

        final BidRequest bidRequest = BidRequest.builder()
                .cur(singletonList("BAD"))
                .imp(singletonList(givenImp(doubleMap("bidder1", 2, "bidder2", 3),
                        identity()))).build();

        final BigDecimal updatedPrice = BigDecimal.valueOf(20);
        given(currencyService.convertCurrency(any(), any(), any(), any())).willReturn(updatedPrice);
        given(currencyService.convertCurrency(any(), any(), eq("CUR"), eq("BAD")))
                .willThrow(new PreBidException("Unable to convert bid currency CUR to desired ad server currency BAD"));

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        // when
        final List<AuctionParticipation> result = target
                .validateAndAdjustBids(auctionParticipations, auctionContext, null);

        // then
        verify(currencyService).convertCurrency(eq(firstBidderPrice), eq(bidRequest), eq("USD"), eq("BAD"));
        verify(currencyService).convertCurrency(eq(secondBidderPrice), eq(bidRequest), eq("CUR"), eq("BAD"));

        assertThat(result).hasSize(1);

        final ObjectNode expectedBidExt = mapper.createObjectNode();
        expectedBidExt.put("origbidcpm", new BigDecimal("2.0"));
        expectedBidExt.put("origbidcur", "USD");
        final Bid expectedBid = Bid.builder().impid("impId1").price(updatedPrice).ext(expectedBidExt).build();
        final BidderBid expectedBidderBid = BidderBid.of(expectedBid, banner, "USD");
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .containsOnly(expectedBidderBid);

        final BidderError expectedError =
                BidderError.generic("Unable to convert bid currency CUR to desired ad server currency BAD");
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getErrors)
                .containsOnly(expectedError);
    }

    @Test
    public void shouldUpdateBidPriceWithCurrencyConversionAndAddWarningAboutMultipleCurrency() {
        // given
        final BigDecimal bidderPrice = BigDecimal.valueOf(2.0);
        final BidderResponse bidderResponse = BidderResponse.of(
                "bidder",
                BidderSeatBid.builder()
                        .bids(List.of(
                                givenBidderBid(Bid.builder().impid("impId1").price(bidderPrice).build(), "USD")
                        ))
                        .build(),
                1);

        final BidRequest bidRequest = givenBidRequest(
                singletonList(givenImp(singletonMap("bidder", 2), identity())),
                builder -> builder.cur(List.of("CUR1", "CUR2", "CUR2")));

        final BigDecimal updatedPrice = BigDecimal.valueOf(10.0);
        given(currencyService.convertCurrency(any(), any(), any(), any())).willReturn(updatedPrice);

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        // when
        final List<AuctionParticipation> result = target
                .validateAndAdjustBids(auctionParticipations, auctionContext, null);

        // then
        verify(currencyService).convertCurrency(eq(bidderPrice), eq(bidRequest), eq("USD"), eq("CUR1"));

        assertThat(result).hasSize(1);

        final BidderSeatBid firstSeatBid = result.getFirst().getBidderResponse().getSeatBid();
        assertThat(firstSeatBid.getBids())
                .extracting(BidderBid::getBid)
                .flatExtracting(Bid::getPrice)
                .containsOnly(updatedPrice);

        final BidderError expectedWarning = BidderError.badInput(
                "a single currency (CUR1) has been chosen for the request. "
                        + "ORTB 2.6 requires that all responses are in the same currency.");
        assertThat(firstSeatBid.getWarnings()).containsOnly(expectedWarning);
    }

    @Test
    public void shouldUpdateBidPriceWithCurrencyConversionForMultipleBid() {
        // given
        final BigDecimal bidder1Price = BigDecimal.valueOf(1.5);
        final BigDecimal bidder2Price = BigDecimal.valueOf(2);
        final BigDecimal bidder3Price = BigDecimal.valueOf(3);
        final BidderResponse bidderResponse = BidderResponse.of(
                "bidder",
                BidderSeatBid.builder()
                        .bids(List.of(
                                givenBidderBid(Bid.builder().impid("impId1").price(bidder1Price).build(), "EUR"),
                                givenBidderBid(Bid.builder().impid("impId2").price(bidder2Price).build(), "GBP"),
                                givenBidderBid(Bid.builder().impid("impId3").price(bidder3Price).build(), "USD")
                        ))
                        .build(),
                1);

        final BidRequest bidRequest = givenBidRequest(
                singletonList(givenImp(Map.of("bidder1", 1), identity())),
                builder -> builder.cur(singletonList("USD")));

        final BigDecimal updatedPrice = BigDecimal.valueOf(10.0);
        given(currencyService.convertCurrency(any(), any(), any(), any())).willReturn(updatedPrice);
        given(currencyService.convertCurrency(any(), any(), eq("USD"), any())).willReturn(bidder3Price);

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        // when
        final List<AuctionParticipation> result = target
                .validateAndAdjustBids(auctionParticipations, auctionContext, null);

        // then
        verify(currencyService).convertCurrency(eq(bidder1Price), eq(bidRequest), eq("EUR"), eq("USD"));
        verify(currencyService).convertCurrency(eq(bidder2Price), eq(bidRequest), eq("GBP"), eq("USD"));
        verify(currencyService).convertCurrency(eq(bidder3Price), eq(bidRequest), eq("USD"), eq("USD"));
        verifyNoMoreInteractions(currencyService);

        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsOnly(bidder3Price, updatedPrice, updatedPrice);
    }

    @Test
    public void shouldReturnBidsWithAdjustedPricesWhenAdjustmentFactorPresent() {
        // given
        final BidderResponse bidderResponse = givenBidderResponse(
                Bid.builder().impid("impId").price(BigDecimal.valueOf(2)).build());

        final ExtRequestBidAdjustmentFactors givenAdjustments = ExtRequestBidAdjustmentFactors.builder().build();
        givenAdjustments.addFactor("bidder", BigDecimal.valueOf(2.468));
        given(bidAdjustmentFactorResolver.resolve(ImpMediaType.banner, givenAdjustments, "bidder"))
                .willReturn(BigDecimal.valueOf(2.468));

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(emptyMap())
                        .bidadjustmentfactors(givenAdjustments)
                        .auctiontimestamp(1000L)
                        .build())));

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        // when
        final List<AuctionParticipation> result = target
                .validateAndAdjustBids(auctionParticipations, auctionContext, null);

        // then
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(BigDecimal.valueOf(4.936));
    }

    @Test
    public void shouldReturnBidsWithAdjustedPricesWithVideoInstreamMediaTypeIfVideoPlacementEqualsOne() {
        // given
        final BidderResponse bidderResponse = BidderResponse.of(
                "bidder",
                BidderSeatBid.builder()
                        .bids(List.of(
                                givenBidderBid(Bid.builder()
                                                .impid("123")
                                                .price(BigDecimal.valueOf(2)).build(),
                                        "USD", video)
                        ))
                        .build(),
                1);

        final ExtRequestBidAdjustmentFactors givenAdjustments = ExtRequestBidAdjustmentFactors.builder()
                .mediatypes(new EnumMap<>(singletonMap(ImpMediaType.video,
                        singletonMap("bidder", BigDecimal.valueOf(3.456)))))
                .build();
        given(bidAdjustmentFactorResolver.resolve(ImpMediaType.video, givenAdjustments, "bidder"))
                .willReturn(BigDecimal.valueOf(3.456));

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), impBuilder ->
                        impBuilder.id("123").video(Video.builder().placement(1).build()))),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(emptyMap())
                        .bidadjustmentfactors(givenAdjustments)
                        .auctiontimestamp(1000L)
                        .build())));

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        // when
        final List<AuctionParticipation> result = target
                .validateAndAdjustBids(auctionParticipations, auctionContext, null);

        // then
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(BigDecimal.valueOf(6.912));
    }

    @Test
    public void shouldReturnBidsWithAdjustedPricesWithVideoInstreamMediaTypeIfVideoPlacementIsMissing() {
        // given
        final BidderResponse bidderResponse = BidderResponse.of(
                "bidder",
                BidderSeatBid.builder()
                        .bids(List.of(
                                givenBidderBid(Bid.builder()
                                                .impid("123")
                                                .price(BigDecimal.valueOf(2)).build(),
                                        "USD", video)
                        ))
                        .build(),
                1);

        final ExtRequestBidAdjustmentFactors givenAdjustments = ExtRequestBidAdjustmentFactors.builder()
                .mediatypes(new EnumMap<>(singletonMap(ImpMediaType.video,
                        singletonMap("bidder", BigDecimal.valueOf(3.456)))))
                .build();
        given(bidAdjustmentFactorResolver.resolve(ImpMediaType.video, givenAdjustments, "bidder"))
                .willReturn(BigDecimal.valueOf(3.456));

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), impBuilder ->
                        impBuilder.id("123").video(Video.builder().build()))),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(emptyMap())
                        .bidadjustmentfactors(givenAdjustments)
                        .auctiontimestamp(1000L)
                        .build())));

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        // when
        final List<AuctionParticipation> result = target
                .validateAndAdjustBids(auctionParticipations, auctionContext, null);

        // then
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(BigDecimal.valueOf(6.912));
    }

    @Test
    public void shouldReturnBidAdjustmentMediaTypeNullIfImpIdNotEqualBidImpId() {
        // given
        final BidderResponse bidderResponse = BidderResponse.of(
                "bidder",
                BidderSeatBid.builder()
                        .bids(List.of(
                                givenBidderBid(Bid.builder()
                                                .impid("123")
                                                .price(BigDecimal.valueOf(2)).build(),
                                        "USD", video)
                        ))
                        .build(),
                1);

        final ExtRequestBidAdjustmentFactors givenAdjustments = ExtRequestBidAdjustmentFactors.builder()
                .mediatypes(new EnumMap<>(singletonMap(ImpMediaType.video,
                        singletonMap("bidder", BigDecimal.valueOf(3.456)))))
                .build();

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), impBuilder ->
                        impBuilder.id("123").video(Video.builder().placement(10).build()))),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(emptyMap())
                        .bidadjustmentfactors(givenAdjustments)
                        .auctiontimestamp(1000L)
                        .build())));

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        // when
        final List<AuctionParticipation> result = target
                .validateAndAdjustBids(auctionParticipations, auctionContext, null);

        // then
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(BigDecimal.valueOf(2));
    }

    @Test
    public void shouldReturnBidAdjustmentMediaTypeVideoOutStreamIfImpIdEqualBidImpIdAndPopulatedPlacement() {
        // given
        final BidderResponse bidderResponse = BidderResponse.of(
                "bidder",
                BidderSeatBid.builder()
                        .bids(List.of(
                                givenBidderBid(Bid.builder()
                                                .impid("123")
                                                .price(BigDecimal.valueOf(2)).build(),
                                        "USD", video)
                        ))
                        .build(),
                1);

        final ExtRequestBidAdjustmentFactors givenAdjustments = ExtRequestBidAdjustmentFactors.builder()
                .mediatypes(new EnumMap<>(singletonMap(ImpMediaType.video,
                        singletonMap("bidder", BigDecimal.valueOf(3.456)))))
                .build();

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), impBuilder ->
                        impBuilder.id("123").video(Video.builder().placement(10).build()))),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(emptyMap())
                        .bidadjustmentfactors(givenAdjustments)
                        .auctiontimestamp(1000L)
                        .build())));

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        // when
        final List<AuctionParticipation> result = target
                .validateAndAdjustBids(auctionParticipations, auctionContext, null);

        // then
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(BigDecimal.valueOf(2));
    }

    @Test
    public void shouldReturnBidsWithAdjustedPricesWhenAdjustmentMediaFactorPresent() {
        // given
        final BidderResponse bidderResponse = BidderResponse.of(
                "bidder",
                BidderSeatBid.builder()
                        .bids(List.of(
                                givenBidderBid(Bid.builder().price(BigDecimal.valueOf(2)).build(), "USD", banner),
                                givenBidderBid(Bid.builder().price(BigDecimal.ONE).build(), "USD", xNative),
                                givenBidderBid(Bid.builder().price(BigDecimal.ONE).build(), "USD", audio)))
                        .build(),
                1);

        final ExtRequestBidAdjustmentFactors givenAdjustments = ExtRequestBidAdjustmentFactors.builder()
                .mediatypes(new EnumMap<>(singletonMap(ImpMediaType.banner,
                        singletonMap("bidder", BigDecimal.valueOf(3.456)))))
                .build();
        given(bidAdjustmentFactorResolver.resolve(ImpMediaType.banner, givenAdjustments, "bidder"))
                .willReturn(BigDecimal.valueOf(3.456));

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(emptyMap())
                        .bidadjustmentfactors(givenAdjustments)
                        .auctiontimestamp(1000L)
                        .build())));

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        // when
        final List<AuctionParticipation> result = target
                .validateAndAdjustBids(auctionParticipations, auctionContext, null);

        // then
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(BigDecimal.valueOf(6.912), BigDecimal.valueOf(1), BigDecimal.valueOf(1));
    }

    @Test
    public void shouldAdjustPriceWithPriorityForMediaTypeAdjustment() {
        // given
        final BidderResponse bidderResponse = BidderResponse.of(
                "bidder",
                BidderSeatBid.builder()
                        .bids(List.of(
                                givenBidderBid(Bid.builder()
                                                .impid("123")
                                                .price(BigDecimal.valueOf(2)).build(),
                                        "USD")
                        ))
                        .build(),
                1);

        final ExtRequestBidAdjustmentFactors givenAdjustments = ExtRequestBidAdjustmentFactors.builder()
                .mediatypes(new EnumMap<>(singletonMap(ImpMediaType.banner,
                        singletonMap("bidder", BigDecimal.valueOf(3.456)))))
                .build();
        givenAdjustments.addFactor("bidder", BigDecimal.valueOf(2.468));
        given(bidAdjustmentFactorResolver.resolve(ImpMediaType.banner, givenAdjustments, "bidder"))
                .willReturn(BigDecimal.valueOf(3.456));

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(emptyMap())
                        .bidadjustmentfactors(givenAdjustments)
                        .auctiontimestamp(1000L)
                        .build())));

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        // when
        final List<AuctionParticipation> result = target
                .validateAndAdjustBids(auctionParticipations, auctionContext, null);

        // then
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(BigDecimal.valueOf(6.912));
    }

    @Test
    public void shouldReturnBidsWithoutAdjustingPricesWhenAdjustmentFactorNotPresentForBidder() {
        // given
        final BidderResponse bidderResponse = BidderResponse.of(
                "bidder",
                BidderSeatBid.builder()
                        .bids(List.of(
                                givenBidderBid(Bid.builder()
                                                .impid("123")
                                                .price(BigDecimal.ONE).build(),
                                        "USD")
                        ))
                        .build(),
                1);

        final ExtRequestBidAdjustmentFactors givenAdjustments = ExtRequestBidAdjustmentFactors.builder().build();
        givenAdjustments.addFactor("some-other-bidder", BigDecimal.TEN);

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(emptyMap())
                        .auctiontimestamp(1000L)
                        .currency(ExtRequestCurrency.of(null, false))
                        .bidadjustmentfactors(givenAdjustments)
                        .build())));

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        // when
        final List<AuctionParticipation> result = target
                .validateAndAdjustBids(auctionParticipations, auctionContext, null);

        // then
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(BigDecimal.ONE);
    }

    @Test
    public void shouldReturnBidsAcceptedByPriceFloorEnforcer() {
        // given
        final BidderBid bidToAccept =
                givenBidderBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.ONE).build(), "USD");
        final BidderBid bidToReject =
                givenBidderBid(Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.TEN).build(), "USD");
        final BidderResponse bidderResponse = BidderResponse.of(
                "bidder",
                BidderSeatBid.builder()
                        .bids(List.of(bidToAccept, bidToReject))
                        .build(),
                1);

        final BidRequest bidRequest = givenBidRequest(List.of(
                        // imp ids are not really used for matching, included them here for clarity
                        givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1")),
                        givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId2"))),
                identity());

        given(priceFloorEnforcer.enforce(any(), any(), any(), any()))
                .willReturn(AuctionParticipation.builder()
                        .bidder("bidder1")
                        .bidderResponse(BidderResponse.of(
                                "bidder1", BidderSeatBid.of(singletonList(bidToAccept)), 0))
                        .build());

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        // when
        final List<AuctionParticipation> result = target
                .validateAndAdjustBids(auctionParticipations, auctionContext, null);

        // then
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .containsExactly(bidToAccept);
    }

    @Test
    public void shouldReturnBidsAcceptedByDsaEnforcer() {
        // given
        final BidderBid bidToAccept =
                givenBidderBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.ONE).build(), "USD");
        final BidderBid bidToReject =
                givenBidderBid(Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.TEN).build(), "USD");

        final BidderResponse bidderResponse = BidderResponse.of(
                "bidder",
                BidderSeatBid.builder()
                        .bids(List.of(bidToAccept, bidToReject))
                        .build(),
                1);

        final BidRequest bidRequest = givenBidRequest(List.of(
                        // imp ids are not really used for matching, included them here for clarity
                        givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1")),
                        givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId2"))),
                identity());

        given(dsaEnforcer.enforce(any(), any(), any()))
                .willReturn(AuctionParticipation.builder()
                        .bidder("bidder1")
                        .bidderResponse(BidderResponse.of(
                                "bidder1", BidderSeatBid.of(singletonList(bidToAccept)), 0))
                        .build());

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        // when
        final List<AuctionParticipation> result = target
                .validateAndAdjustBids(auctionParticipations, auctionContext, null);

        // then
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .containsExactly(bidToAccept);
    }

    @Test
    public void shouldTolerateResponseBidValidationErrors() {
        // given
        final BidderResponse bidderResponse = BidderResponse.of(
                "bidder",
                BidderSeatBid.builder()
                        .bids(List.of(givenBidderBid(
                                Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(1.23)).build())))
                        .build(),
                1);

        final BidRequest bidRequest = givenBidRequest(singletonList(
                        // imp ids are not really used for matching, included them here for clarity
                        givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1"))),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .auctiontimestamp(1000L)
                        .build())));

        when(responseBidValidator.validate(any(), any(), any(), any()))
                .thenReturn(ValidationResult.error("Error: bid validation error."));

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        // when
        final List<AuctionParticipation> result = target
                .validateAndAdjustBids(auctionParticipations, auctionContext, null);

        // then
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .isEmpty();
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getErrors)
                .containsOnly(
                        BidderError.invalidBid(
                                "BidId `bidId1` validation messages: Error: Error: bid validation error."));
    }

    @Test
    public void shouldTolerateResponseBidValidationWarnings() {
        // given
        final BidderResponse bidderResponse = BidderResponse.of(
                "bidder",
                BidderSeatBid.builder()
                        .bids(List.of(givenBidderBid(
                                Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(1.23)).build())))
                        .build(),
                1);

        final BidRequest bidRequest = givenBidRequest(singletonList(
                        // imp ids are not really used for matching, included them here for clarity
                        givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1"))),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .auctiontimestamp(1000L)
                        .build())));

        when(responseBidValidator.validate(any(), any(), any(), any()))
                .thenReturn(ValidationResult.warning(singletonList("Error: bid validation warning.")));

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        // when
        final List<AuctionParticipation> result = target
                .validateAndAdjustBids(auctionParticipations, auctionContext, null);

        // then
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .hasSize(1);
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getErrors)
                .containsOnly(BidderError.invalidBid(
                        "BidId `bidId1` validation messages: Warning: Error: bid validation warning."));
    }

    private BidderResponse givenBidderResponse(Bid bid) {
        return BidderResponse.of(
                "bidder",
                BidderSeatBid.builder()
                        .bids(singletonList(givenBidderBid(bid)))
                        .build(),
                1);
    }

    private List<AuctionParticipation> givenAuctionParticipation(
            BidderResponse bidderResponse, BidRequest bidRequest) {

        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidRequest(bidRequest)
                .build();

        return List.of(AuctionParticipation.builder()
                .bidder("bidder")
                .bidderRequest(bidderRequest)
                .bidderResponse(bidderResponse)
                .build());
    }

    private AuctionContext givenAuctionContext(BidRequest bidRequest) {
        return AuctionContext.builder()
                .bidRequest(bidRequest)
                .bidRejectionTrackers(Map.of("bidder", new BidRejectionTracker("bidder", Set.of(), 1)))
                .build();
    }

    private static BidRequest givenBidRequest(List<Imp> imp,
                                              UnaryOperator<BidRequest.BidRequestBuilder> bidRequestBuilderCustomizer) {

        return bidRequestBuilderCustomizer
                .apply(BidRequest.builder().cur(singletonList("USD")).imp(imp).tmax(500L))
                .build();
    }

    private static <T> Imp givenImp(T ext, Function<Imp.ImpBuilder, Imp.ImpBuilder> impBuilderCustomizer) {
        return impBuilderCustomizer.apply(Imp.builder()
                        .id(UUID.randomUUID().toString())
                        .ext(mapper.valueToTree(singletonMap(
                                "prebid", ext != null ? singletonMap("bidder", ext) : emptyMap()))))
                .build();
    }

    private static BidderBid givenBidderBid(Bid bid) {
        return BidderBid.of(bid, banner, null);
    }

    private static BidderBid givenBidderBid(Bid bid, String currency) {
        return BidderBid.of(bid, banner, currency);
    }

    private static BidderBid givenBidderBid(Bid bid, String currency, BidType type) {
        return BidderBid.of(bid, type, currency);
    }

    private static <K, V> Map<K, V> doubleMap(K key1, V value1, K key2, V value2) {
        final Map<K, V> map = new HashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }
}
