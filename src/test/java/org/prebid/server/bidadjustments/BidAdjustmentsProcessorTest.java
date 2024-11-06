package org.prebid.server.bidadjustments;

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
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidadjustments.model.BidAdjustments;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentFactors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustments;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestCurrency;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.prebid.server.proto.openrtb.ext.response.BidType.audio;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

@ExtendWith(MockitoExtension.class)
public class BidAdjustmentsProcessorTest extends VertxTest {

    @Mock(strictness = LENIENT)
    private CurrencyConversionService currencyService;
    @Mock(strictness = LENIENT)
    private BidAdjustmentFactorResolver bidAdjustmentFactorResolver;
    @Mock(strictness = LENIENT)
    private BidAdjustmentsResolver bidAdjustmentsResolver;

    private BidAdjustmentsProcessor target;

    @BeforeEach
    public void before() {
        given(currencyService.convertCurrency(any(), any(), any(), any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(bidAdjustmentsResolver.resolve(any(), any(), any(), any(), any(), any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        target = new BidAdjustmentsProcessor(
                currencyService,
                bidAdjustmentFactorResolver,
                bidAdjustmentsResolver,
                jacksonMapper);
    }

    @Test
    public void shouldReturnBidsWithUpdatedPriceCurrencyConversionAndAdjusted() {
        // given
        final BidderResponse bidderResponse = givenBidderResponse(
                Bid.builder().impid("impId").price(BigDecimal.valueOf(2.0)).dealid("dealId").build());
        final BidRequest bidRequest = givenBidRequest(
                singletonList(givenImp(singletonMap("bidder", 2), identity())), identity());

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(bidderResponse, bidRequest);

        final BigDecimal convertedPrice = BigDecimal.valueOf(5.0);
        given(currencyService.convertCurrency(any(), any(), any(), any())).willReturn(convertedPrice);

        final Price adjustedPrice = Price.of("EUR", BigDecimal.valueOf(5.0));
        given(bidAdjustmentsResolver.resolve(any(), any(), any(), any(), any(), any())).willReturn(adjustedPrice);

        // when
        final AuctionParticipation result = target.enrichWithAdjustedBids(
                auctionParticipation, bidRequest, givenBidAdjustments());

        // then
        assertThat(result.getBidderResponse().getSeatBid().getBids())
                .extracting(BidderBid::getBidCurrency, bidderBid -> bidderBid.getBid().getPrice())
                .containsExactly(tuple(adjustedPrice.getCurrency(), adjustedPrice.getValue()));

        verify(bidAdjustmentsResolver).resolve(
                eq(Price.of("USD", convertedPrice)),
                eq(bidRequest),
                eq(givenBidAdjustments()),
                eq(ImpMediaType.banner),
                eq("bidder"),
                eq("dealId"));
    }

    @Test
    public void shouldReturnSameBidPriceIfNoChangesAppliedToBidPrice() {
        // given
        final BidderResponse bidderResponse = givenBidderResponse(
                Bid.builder().impid("impId").price(BigDecimal.valueOf(2.0)).build());
        final BidRequest bidRequest = givenBidRequest(
                singletonList(givenImp(singletonMap("bidder", 2), identity())), identity());

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(bidderResponse, bidRequest);

        given(currencyService.convertCurrency(any(), any(), any(), any()))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(bidAdjustmentsResolver.resolve(any(), any(), any(), any(), any(), any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        final AuctionParticipation result = target.enrichWithAdjustedBids(
                auctionParticipation, bidRequest, givenBidAdjustments());

        // then
        assertThat(result.getBidderResponse().getSeatBid().getBids())
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

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(bidderResponse, bidRequest);

        given(currencyService.convertCurrency(any(), any(), any(), any()))
                .willThrow(new PreBidException("Unable to convert bid currency CUR to desired ad server currency USD"));

        // when
        final AuctionParticipation result = target.enrichWithAdjustedBids(
                auctionParticipation, bidRequest, givenBidAdjustments());

        // then
        final BidderError expectedError = BidderError.generic(
                "Unable to convert bid currency CUR to desired ad server currency USD");
        final BidderSeatBid firstSeatBid = result.getBidderResponse().getSeatBid();
        assertThat(firstSeatBid.getBids()).isEmpty();
        assertThat(firstSeatBid.getErrors()).containsOnly(expectedError);

        verifyNoInteractions(bidAdjustmentsResolver);
    }

    @Test
    public void shouldDropBidIfPrebidExceptionWasThrownDuringBidAdjustmentResolving() {
        // given
        final BidderResponse bidderResponse = givenBidderResponse(
                Bid.builder().impid("impId").price(BigDecimal.valueOf(2.0)).build());
        final BidRequest bidRequest = givenBidRequest(
                singletonList(givenImp(singletonMap("bidder", 2), identity())), identity());

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(bidderResponse, bidRequest);

        given(currencyService.convertCurrency(any(), any(), any(), any()))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(bidAdjustmentsResolver.resolve(any(), any(), any(), any(), any(), any()))
                .willThrow(new PreBidException("Unable to convert bid currency CUR to desired ad server currency USD"));

        // when
        final AuctionParticipation result = target.enrichWithAdjustedBids(
                auctionParticipation, bidRequest, givenBidAdjustments());

        // then
        final BidderError expectedError = BidderError.generic(
                "Unable to convert bid currency CUR to desired ad server currency USD");
        final BidderSeatBid firstSeatBid = result.getBidderResponse().getSeatBid();
        assertThat(firstSeatBid.getBids()).isEmpty();
        assertThat(firstSeatBid.getErrors()).containsOnly(expectedError);
    }

    @Test
    public void shouldUpdateBidPriceWithCurrencyConversionAndPriceAdjustmentFactorAndBidAdjustments() {
        // given
        final BidderResponse bidderResponse = givenBidderResponse(
                Bid.builder().impid("impId").price(BigDecimal.valueOf(2.0)).dealid("dealId").build());

        final ExtRequestBidAdjustmentFactors givenAdjustments = ExtRequestBidAdjustmentFactors.builder().build();
        givenAdjustments.addFactor("bidder", BigDecimal.TEN);

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(emptyMap())
                        .bidadjustmentfactors(givenAdjustments)
                        .auctiontimestamp(1000L)
                        .build())));

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(bidderResponse, bidRequest);

        given(bidAdjustmentFactorResolver.resolve(ImpMediaType.banner, givenAdjustments, "bidder"))
                .willReturn(BigDecimal.TEN);
        given(currencyService.convertCurrency(any(), any(), any(), any()))
                .willReturn(BigDecimal.TEN);
        final Price adjustedPrice = Price.of("EUR", BigDecimal.valueOf(5.0));
        given(bidAdjustmentsResolver.resolve(any(), any(), any(), any(), any(), any())).willReturn(adjustedPrice);

        // when
        final AuctionParticipation result = target.enrichWithAdjustedBids(
                auctionParticipation, bidRequest, givenBidAdjustments());

        // then
        final BigDecimal updatedPrice = BigDecimal.valueOf(100);
        final BidderSeatBid seatBid = result.getBidderResponse().getSeatBid();
        assertThat(seatBid.getBids())
                .extracting(BidderBid::getBidCurrency, bidderBid -> bidderBid.getBid().getPrice())
                .containsExactly(tuple(adjustedPrice.getCurrency(), adjustedPrice.getValue()));
        assertThat(seatBid.getErrors()).isEmpty();

        verify(bidAdjustmentsResolver).resolve(
                eq(Price.of("USD", updatedPrice)),
                eq(bidRequest),
                eq(givenBidAdjustments()),
                eq(ImpMediaType.banner),
                eq("bidder"),
                eq("dealId"));
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

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(bidderResponse, bidRequest);

        // when
        final AuctionParticipation result = target
                .enrichWithAdjustedBids(auctionParticipation, bidRequest, null);

        // then
        verify(currencyService).convertCurrency(eq(firstBidderPrice), eq(bidRequest), eq("CUR1"), any());
        verify(currencyService).convertCurrency(eq(secondBidderPrice), eq(bidRequest), eq("CUR2"), any());

        final ObjectNode expectedBidExt = mapper.createObjectNode();
        expectedBidExt.put("origbidcpm", new BigDecimal("2.0"));
        expectedBidExt.put("origbidcur", "CUR1");
        final Bid expectedBid = Bid.builder().impid("impId1").price(updatedPrice).ext(expectedBidExt).build();
        final BidderBid expectedBidderBid = BidderBid.of(expectedBid, banner, "CUR1");
        final BidderError expectedError =
                BidderError.generic("Unable to convert bid currency CUR2 to desired ad server currency USD");

        final BidderSeatBid firstSeatBid = result.getBidderResponse().getSeatBid();
        assertThat(firstSeatBid.getBids()).containsOnly(expectedBidderBid);
        assertThat(firstSeatBid.getErrors()).containsOnly(expectedError);

        verify(bidAdjustmentsResolver).resolve(any(), any(), any(), any(), any(), any());
        verifyNoMoreInteractions(bidAdjustmentsResolver);
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

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(bidderResponse, bidRequest);

        // when
        final AuctionParticipation result = target.enrichWithAdjustedBids(
                auctionParticipation, bidRequest, givenBidAdjustments());

        // then
        verify(currencyService).convertCurrency(eq(firstBidderPrice), eq(bidRequest), eq("USD"), eq("BAD"));
        verify(currencyService).convertCurrency(eq(secondBidderPrice), eq(bidRequest), eq("CUR"), eq("BAD"));

        final ObjectNode expectedBidExt = mapper.createObjectNode();
        expectedBidExt.put("origbidcpm", new BigDecimal("2.0"));
        expectedBidExt.put("origbidcur", "USD");
        final Bid expectedBid = Bid.builder().impid("impId1").price(updatedPrice).ext(expectedBidExt).build();
        final BidderBid expectedBidderBid = BidderBid.of(expectedBid, banner, "USD");
        assertThat(result.getBidderResponse().getSeatBid().getBids()).containsOnly(expectedBidderBid);

        final BidderError expectedError = BidderError.generic(
                "Unable to convert bid currency CUR to desired ad server currency BAD");
        assertThat(result.getBidderResponse().getSeatBid().getErrors()).containsOnly(expectedError);
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

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(bidderResponse, bidRequest);

        // when
        final AuctionParticipation result = target.enrichWithAdjustedBids(
                auctionParticipation, bidRequest, givenBidAdjustments());

        // then
        verify(currencyService).convertCurrency(eq(bidder1Price), eq(bidRequest), eq("EUR"), eq("USD"));
        verify(currencyService).convertCurrency(eq(bidder2Price), eq(bidRequest), eq("GBP"), eq("USD"));
        verify(currencyService).convertCurrency(eq(bidder3Price), eq(bidRequest), eq("USD"), eq("USD"));
        verifyNoMoreInteractions(currencyService);

        assertThat(result.getBidderResponse().getSeatBid().getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsOnly(bidder3Price, updatedPrice, updatedPrice);

        verify(bidAdjustmentsResolver, times(3)).resolve(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void shouldReturnBidsWithAdjustedPricesWhenAdjustmentFactorPresent() {
        // given
        final BidderResponse bidderResponse = givenBidderResponse(
                Bid.builder().impid("impId").price(BigDecimal.valueOf(2)).dealid("dealId").build());

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

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(bidderResponse, bidRequest);

        // when
        final AuctionParticipation result = target.enrichWithAdjustedBids(
                auctionParticipation, bidRequest, givenBidAdjustments());

        // then
        assertThat(result.getBidderResponse().getSeatBid().getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(BigDecimal.valueOf(4.936));

        verify(bidAdjustmentsResolver).resolve(
                eq(Price.of("USD", BigDecimal.valueOf(4.936))),
                eq(bidRequest),
                eq(givenBidAdjustments()),
                eq(ImpMediaType.banner),
                eq("bidder"),
                eq("dealId"));
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
                                                .price(BigDecimal.valueOf(2))
                                                .dealid("dealId")
                                                .build(),
                                        "USD", video)))
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

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(bidderResponse, bidRequest);

        // when
        final AuctionParticipation result = target.enrichWithAdjustedBids(
                auctionParticipation, bidRequest, givenBidAdjustments());

        // then
        assertThat(result.getBidderResponse().getSeatBid().getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(BigDecimal.valueOf(6.912));

        verify(bidAdjustmentsResolver).resolve(
                eq(Price.of("USD", BigDecimal.valueOf(6.912))),
                eq(bidRequest),
                eq(givenBidAdjustments()),
                eq(ImpMediaType.video_instream),
                eq("bidder"),
                eq("dealId"));
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
                                                .price(BigDecimal.valueOf(2))
                                                .dealid("dealId")
                                                .build(),
                                        "USD", video)))
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

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(bidderResponse, bidRequest);
        // when
        final AuctionParticipation result = target
                .enrichWithAdjustedBids(auctionParticipation, bidRequest, givenBidAdjustments());

        // then
        assertThat(result.getBidderResponse().getSeatBid().getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(BigDecimal.valueOf(6.912));

        verify(bidAdjustmentsResolver).resolve(
                eq(Price.of("USD", BigDecimal.valueOf(6.912))),
                eq(bidRequest),
                eq(givenBidAdjustments()),
                eq(ImpMediaType.video_instream),
                eq("bidder"),
                eq("dealId"));
    }

    @Test
    public void shouldReturnBidAdjustmentMediaTypeNullIfImpIdNotEqualBidImpId() {
        // given
        final BidderResponse bidderResponse = BidderResponse.of(
                "bidder",
                BidderSeatBid.builder()
                        .bids(List.of(
                                givenBidderBid(Bid.builder()
                                                .impid("125")
                                                .price(BigDecimal.valueOf(2))
                                                .dealid("dealId")
                                                .build(),
                                        "USD", video)))
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

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(bidderResponse, bidRequest);

        // when
        final AuctionParticipation result = target
                .enrichWithAdjustedBids(auctionParticipation, bidRequest, givenBidAdjustments());

        // then
        assertThat(result.getBidderResponse().getSeatBid().getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(BigDecimal.valueOf(2));

        verify(bidAdjustmentsResolver).resolve(
                eq(Price.of("USD", BigDecimal.valueOf(2))),
                eq(bidRequest),
                eq(givenBidAdjustments()),
                eq(ImpMediaType.video_instream),
                eq("bidder"),
                eq("dealId"));
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
                                                .price(BigDecimal.valueOf(2))
                                                .dealid("dealId")
                                                .build(),
                                        "USD", video)))
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

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(bidderResponse, bidRequest);

        // when
        final AuctionParticipation result = target
                .enrichWithAdjustedBids(auctionParticipation, bidRequest, givenBidAdjustments());

        // then
        assertThat(result.getBidderResponse().getSeatBid().getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(BigDecimal.valueOf(2));

        verify(bidAdjustmentsResolver).resolve(
                eq(Price.of("USD", BigDecimal.valueOf(2))),
                eq(bidRequest),
                eq(givenBidAdjustments()),
                eq(ImpMediaType.video_outstream),
                eq("bidder"),
                eq("dealId"));
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

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(bidderResponse, bidRequest);

        // when
        final AuctionParticipation result = target.enrichWithAdjustedBids(
                auctionParticipation, bidRequest, givenBidAdjustments());

        // then
        assertThat(result.getBidderResponse().getSeatBid().getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(BigDecimal.valueOf(6.912), BigDecimal.valueOf(1), BigDecimal.valueOf(1));

        verify(bidAdjustmentsResolver, times(3))
                .resolve(any(), any(), any(), any(), any(), any());
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
                                                .price(BigDecimal.valueOf(2))
                                                .dealid("dealId")
                                                .build(),
                                        "USD")))
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

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(bidderResponse, bidRequest);

        // when
        final AuctionParticipation result = target.enrichWithAdjustedBids(
                auctionParticipation, bidRequest, givenBidAdjustments());

        // then
        assertThat(result.getBidderResponse().getSeatBid().getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsOnly(BigDecimal.valueOf(6.912));

        verify(bidAdjustmentsResolver).resolve(
                eq(Price.of("USD", BigDecimal.valueOf(6.912))),
                eq(bidRequest),
                eq(givenBidAdjustments()),
                eq(ImpMediaType.banner),
                eq("bidder"),
                eq("dealId"));
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
                                                .price(BigDecimal.ONE)
                                                .dealid("dealId")
                                                .build(),
                                        "USD")))
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

        final AuctionParticipation auctionParticipation = givenAuctionParticipation(bidderResponse, bidRequest);

        // when
        final AuctionParticipation result = target
                .enrichWithAdjustedBids(auctionParticipation, bidRequest, givenBidAdjustments());

        // then
        assertThat(result.getBidderResponse().getSeatBid().getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(BigDecimal.ONE);

        verify(bidAdjustmentsResolver).resolve(
                eq(Price.of("USD", BigDecimal.ONE)),
                eq(bidRequest),
                eq(givenBidAdjustments()),
                eq(ImpMediaType.banner),
                eq("bidder"),
                eq("dealId"));
    }

    private static BidRequest givenBidRequest(List<Imp> imp,
                                              UnaryOperator<BidRequest.BidRequestBuilder> bidRequestBuilderCustomizer) {

        return bidRequestBuilderCustomizer
                .apply(BidRequest.builder().cur(singletonList("USD")).imp(imp).tmax(500L))
                .build();
    }

    private static <T> Imp givenImp(T ext, UnaryOperator<Imp.ImpBuilder> impBuilderCustomizer) {
        return impBuilderCustomizer.apply(Imp.builder()
                        .id(UUID.randomUUID().toString())
                        .ext(mapper.valueToTree(singletonMap(
                                "prebid", ext != null ? singletonMap("bidder", ext) : emptyMap()))))
                .build();
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

    private static BidAdjustments givenBidAdjustments() {
        return BidAdjustments.of(ExtRequestBidAdjustments.builder().build());
    }

    private BidderResponse givenBidderResponse(Bid bid) {
        return BidderResponse.of(
                "bidder",
                BidderSeatBid.builder()
                        .bids(singletonList(givenBidderBid(bid, "USD")))
                        .build(),
                1);
    }

    private AuctionParticipation givenAuctionParticipation(BidderResponse bidderResponse,
                                                           BidRequest bidRequest) {

        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidRequest(bidRequest)
                .build();

        return AuctionParticipation.builder()
                .bidder("bidder")
                .bidderRequest(bidderRequest)
                .bidderResponse(bidderResponse)
                .build();
    }

}
