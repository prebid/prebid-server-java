package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.validation.ResponseBidValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;

public class BidderRequesterTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private HttpBidderRequester httpBidderRequester;
    @Mock
    private ResponseBidValidator responseBidValidator;
    @Mock
    private CurrencyConversionService currencyService;
    @Mock
    private Map<String, BigDecimal> bidAdjustment;

    private BidRequester target;

    private Clock clock;

    private BidderAliases bidderAliases;

    private Timeout timeout;

    @Before
    public void setUp() {
        given(responseBidValidator.validate(any())).willReturn(ValidationResult.success());

        given(currencyService.convertCurrency(any(), any(), any(), any(), any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(bidAdjustment.get(any())).willReturn(BigDecimal.ONE);

        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        timeout = new TimeoutFactory(clock).create(500);

        bidderAliases = BidderAliases.of(emptyMap(), emptyMap(), bidderCatalog);

        target = new BidRequester(
                100,
                bidderCatalog,
                httpBidderRequester,
                responseBidValidator,
                currencyService,
                clock);
    }

    @Test
    public void creationShouldFailOnNegativeExpectedCacheTime() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new BidRequester(
                        -1,
                        bidderCatalog,
                        httpBidderRequester,
                        responseBidValidator,
                        currencyService,
                        clock));
    }

    @Test
    public void shouldReturnSeparateSeatBidsForTheSameBidderIfBiddersAliasAndBidderWereUsedWithingSingleImp() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        doReturn(bidder).when(bidderCatalog).bidderByName(eq("bidder"));

        final BidRequest bidRequest1 = givenBidRequest(givenSingleImp(mapper.valueToTree(ExtPrebid.of(null, 1))),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .auctiontimestamp(1000L)
                        .aliases(singletonMap("bidderAlias", "bidder")).build())));
        given(httpBidderRequester.requestBids(same(bidder), eq(bidRequest1), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(singletonList(
                        givenBid(Bid.builder().price(BigDecimal.ONE).build())))));

        final BidRequest bidRequest2 = givenBidRequest(givenSingleImp(mapper.valueToTree(ExtPrebid.of(null, 2))),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .auctiontimestamp(1000L)
                        .aliases(singletonMap("bidderAlias", "bidder")).build())));
        given(httpBidderRequester.requestBids(same(bidder), eq(bidRequest2), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(singletonList(
                        givenBid(Bid.builder().price(BigDecimal.ONE).build())))));

        final List<AuctionParticipation> participations = toParticipations(asList("bidder", "bidderAlias"),
                asList(bidRequest1, bidRequest2));

        final BidderAliases bidderAliases = BidderAliases.of(singletonMap("bidderAlias", "bidder"), emptyMap(),
                bidderCatalog);

        // when
        final List<AuctionParticipation> auctionParticipations = target.waitForBidResponses(participations, timeout,
                false, false, bidderAliases, bidAdjustment, null, false).result();

        // then
        verify(httpBidderRequester, times(2)).requestBids(any(), any(), any(), anyBoolean());

        assertThat(auctionParticipations).hasSize(2)
                .extracting(participation -> participation.getBidderResponse().getSeatBid().getBids().size())
                .containsOnly(1, 1);
    }

    @Test
    public void shouldTolerateResponseBidValidationErrors() {
        // given
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(1.23)).build()))));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1"))));

        final List<AuctionParticipation> participations = toParticipations(singletonList("bidder1"),
                singletonList(bidRequest));

        given(responseBidValidator.validate(any()))
                .willReturn(ValidationResult.error("bid validation error"));

        final BidderAliases bidderAliases = BidderAliases.of(emptyMap(), emptyMap(), bidderCatalog);

        // when
        final List<AuctionParticipation> result = target.waitForBidResponses(participations, timeout,
                false, false, bidderAliases, bidAdjustment, null, false).result();

        // then
        assertThat(result).hasSize(1)
                .flatExtracting(participation -> participation.getBidderResponse().getSeatBid().getErrors())
                .containsOnly(BidderError.generic("bid validation error"));
    }

    @Test
    public void shouldPassGlobalTimeoutToConnectorUnchangedIfCachingIsNotRequested() {
        // given
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(1.23)).build()))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder1", 1)));
        final List<AuctionParticipation> participations = toParticipations(singletonList("bidder1"),
                singletonList(bidRequest));

        // when
        target.waitForBidResponses(participations, timeout, false, false, bidderAliases, bidAdjustment, null, false)
                .result();

        // then
        verify(httpBidderRequester).requestBids(any(), any(), same(timeout), anyBoolean());
    }

    @Test
    public void shouldReducedByGlobalTimeoutWhenDoCachingIsTrue() {
        // given
        final Bid bid = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        givenBidder(givenSeatBid(singletonList(givenBid(bid))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder1", 1)));
        final List<AuctionParticipation> participations = toParticipations(singletonList("bidder1"),
                singletonList(bidRequest));

        // when
        target.waitForBidResponses(participations, timeout, true, false, bidderAliases, bidAdjustment, null, false)
                .result();

        // then
        final ArgumentCaptor<Timeout> timeoutCaptor = ArgumentCaptor.forClass(Timeout.class);
        verify(httpBidderRequester).requestBids(any(), any(), timeoutCaptor.capture(), anyBoolean());

        assertThat(timeoutCaptor.getValue().remaining()).isEqualTo(400L);
    }

    @Test
    public void shouldReturnBidsWithUpdatedPriceCurrencyConversion() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder1", bidder, givenSeatBid(singletonList(
                givenBid(Bid.builder().price(BigDecimal.valueOf(2.0)).build()))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder1", 1)));
        final List<AuctionParticipation> participations = toParticipations(singletonList("bidder1"),
                singletonList(bidRequest));

        final BigDecimal updatedPrice = BigDecimal.valueOf(5.0);
        given(currencyService.convertCurrency(any(), any(), any(), any(), any())).willReturn(updatedPrice);

        // when
        final List<AuctionParticipation> result = target.waitForBidResponses(participations, timeout, false, false,
                bidderAliases, bidAdjustment, null, false).result();

        // then
        assertThat(result).hasSize(1)
                .flatExtracting(participation -> participation.getBidderResponse().getSeatBid().getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(updatedPrice);
    }

    @Test
    public void shouldReturnSameBidPriceIfNoChangesAppliedToBidPrice() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder1", bidder, givenSeatBid(singletonList(
                givenBid(Bid.builder().price(BigDecimal.ONE).build()))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder1", 1)));
        final List<AuctionParticipation> participations = toParticipations(singletonList("bidder1"),
                singletonList(bidRequest));

        // returns the same price as in argument
        given(currencyService.convertCurrency(any(), any(), any(), any(), any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        final List<AuctionParticipation> result = target.waitForBidResponses(participations, timeout, false, false,
                bidderAliases, bidAdjustment, null, false).result();

        // then
        assertThat(result).hasSize(1)
                .flatExtracting(participation -> participation.getBidderResponse().getSeatBid().getBids())
                .extracting(bidderBid -> bidderBid.getBid().getPrice())
                .containsExactly(BigDecimal.ONE);
    }

    @Test
    public void shouldDropBidIfPrebidExceptionWasThrownDuringCurrencyConversion() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder1", bidder, givenSeatBid(singletonList(
                givenBid(Bid.builder().price(BigDecimal.valueOf(2.0)).build(), "CUR"))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder1", 1)));
        final List<AuctionParticipation> participations = toParticipations(singletonList("bidder1"),
                singletonList(bidRequest));

        given(currencyService.convertCurrency(any(), any(), any(), any(), any()))
                .willThrow(new PreBidException("no currency conversion available"));

        // when
        final List<AuctionParticipation> result = target.waitForBidResponses(participations, timeout, false, false,
                bidderAliases, bidAdjustment, null, false).result();

        // then
        final BidderError expectedError = BidderError.generic("Unable to covert bid currency CUR to desired ad"
                + " server currency USD. no currency conversion available");
        assertThat(result).hasSize(1)
                .extracting(participation -> participation.getBidderResponse().getSeatBid())
                .extracting(BidderSeatBid::getBids, BidderSeatBid::getErrors)
                .containsOnly(tuple(emptyList(), singletonList(expectedError)));
    }

    @Test
    public void shouldUpdateBidPriceWithCurrencyConversionAndPriceAdjustmentFactor() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder1", bidder, givenSeatBid(singletonList(
                givenBid(Bid.builder().price(BigDecimal.valueOf(2.0)).build()))));

        given(bidAdjustment.get(any())).willReturn(BigDecimal.TEN);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder1", 1)));
        final List<AuctionParticipation> participations = toParticipations(singletonList("bidder1"),
                singletonList(bidRequest));

        given(currencyService.convertCurrency(any(), any(), any(), any(), any())).willReturn(BigDecimal.valueOf(10));

        // when
        final List<AuctionParticipation> result = target.waitForBidResponses(participations, timeout, false, false,
                bidderAliases, bidAdjustment, null, false).result();

        // then
        final BigDecimal updatedPrice = BigDecimal.valueOf(100);
        assertThat(result).hasSize(1)
                .flatExtracting(participation -> participation.getBidderResponse().getSeatBid().getBids())
                .extracting(bidderBid -> bidderBid.getBid().getPrice())
                .containsExactly(updatedPrice);
    }

    @Test
    public void shouldUpdatePriceForOneBidAndDropAnotherIfPrebidExceptionHappensForSecondBid() {
        // given
        final BigDecimal firstBidderPrice = BigDecimal.valueOf(2.0);
        final BigDecimal secondBidderPrice = BigDecimal.valueOf(3.0);
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(asList(
                givenBid(Bid.builder().price(firstBidderPrice).build(), "CUR1"),
                givenBid(Bid.builder().price(secondBidderPrice).build(), "CUR2"))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder1", 1)));
        final List<AuctionParticipation> participations = toParticipations(singletonList("bidder1"),
                singletonList(bidRequest));

        final BigDecimal updatedPrice = BigDecimal.valueOf(10.0);
        given(currencyService.convertCurrency(any(), any(), any(), any(), any())).willReturn(updatedPrice)
                .willThrow(new PreBidException("no currency conversion available"));

        // when
        final List<AuctionParticipation> result = target.waitForBidResponses(participations, timeout, false, false,
                bidderAliases, bidAdjustment, null, false).result();

        // then
        verify(currencyService).convertCurrency(eq(firstBidderPrice), eq(null), any(), eq("CUR1"), eq(false));
        verify(currencyService).convertCurrency(eq(secondBidderPrice), eq(null), any(), eq("CUR2"), eq(false));

        final Bid expectedBid = Bid.builder().price(updatedPrice).build();
        final BidderBid expectedBidderBid = BidderBid.of(expectedBid, banner, "CUR1");
        final BidderError expectedError = BidderError.generic("Unable to covert bid currency CUR2 to desired ad"
                + " server currency USD. no currency conversion available");

        assertThat(result).hasSize(1)
                .extracting(participation -> participation.getBidderResponse().getSeatBid())
                .extracting(BidderSeatBid::getBids, BidderSeatBid::getErrors)
                .containsOnly(tuple(singletonList(expectedBidderBid), singletonList(expectedError)));
    }

    @Test
    public void shouldRespondWithOneBidAndErrorWhenBidResponseContainsOneUnsupportedCurrency() {
        // given
        final BigDecimal firstBidderPrice = BigDecimal.valueOf(2.0);
        final BigDecimal secondBidderPrice = BigDecimal.valueOf(10.0);
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().price(firstBidderPrice).build(), "USD"))));
        givenBidder("bidder2", mock(Bidder.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().price(BigDecimal.valueOf(10.0)).build(), "CUR"))));

        final BidRequest bidRequest = BidRequest.builder().cur(singletonList("BAD"))
                .imp(singletonList(givenImp(doubleMap("bidder1", 2, "bidder2", 3),
                        identity()))).build();
        final List<AuctionParticipation> participations = toParticipations(Arrays.asList("bidder1", "bidder2"),
                Arrays.asList(bidRequest, bidRequest));

        final BigDecimal updatedPrice = BigDecimal.valueOf(20);
        given(currencyService.convertCurrency(any(), any(), any(), any(), any())).willReturn(updatedPrice);
        given(currencyService.convertCurrency(any(), any(), eq("BAD"), eq("CUR"), any()))
                .willThrow(new PreBidException("no currency conversion available"));

        // when
        final List<AuctionParticipation> result = target.waitForBidResponses(participations, timeout, false, false,
                bidderAliases, bidAdjustment, null, false).result();

        // then
        verify(currencyService).convertCurrency(eq(firstBidderPrice), eq(null), eq("BAD"), eq("USD"), eq(false));
        verify(currencyService).convertCurrency(eq(secondBidderPrice), eq(null), eq("BAD"), eq("CUR"), eq(false));

        final Bid expectedBid = Bid.builder().price(updatedPrice).build();
        final BidderBid expectedBidderBid = BidderBid.of(expectedBid, banner, "USD");
        final BidderError expectedError = BidderError.generic("Unable to covert bid currency CUR to desired ad"
                + " server currency BAD. no currency conversion available");

        assertThat(result).hasSize(2)
                .extracting(participation -> participation.getBidderResponse().getSeatBid())
                .extracting(BidderSeatBid::getBids, BidderSeatBid::getErrors)
                .containsOnly(
                        tuple(singletonList(expectedBidderBid), emptyList()),
                        tuple(emptyList(), singletonList(expectedError)));
    }

    @Test
    public void shouldUpdateBidPriceWithCurrencyConversionAndAddErrorAboutMultipleCurrency() {
        // given
        final BigDecimal bidderPrice = BigDecimal.valueOf(2.0);
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().price(bidderPrice).build(), "USD"))));

        final BidRequest bidRequest = givenBidRequest(
                singletonList(givenImp(singletonMap("bidder1", 2), identity())),
                builder -> builder.cur(asList("CUR1", "CUR2", "CUR2")));
        final List<AuctionParticipation> participations = toParticipations(singletonList("bidder1"),
                singletonList(bidRequest));

        final BigDecimal updatedPrice = BigDecimal.valueOf(10.0);
        given(currencyService.convertCurrency(any(), any(), any(), any(), any())).willReturn(updatedPrice);

        // when
        final List<AuctionParticipation> result = target.waitForBidResponses(participations, timeout, false, false,
                bidderAliases, bidAdjustment, null, false).result();

        // then
        final Bid expectedBid = Bid.builder().price(updatedPrice).build();
        final BidderBid expectedBidderBid = BidderBid.of(expectedBid, banner, "USD");
        final BidderError expectedError = BidderError.badInput("Cur parameter contains more than one currency."
                + " CUR1 will be used");
        assertThat(result).hasSize(1)
                .extracting(participation -> participation.getBidderResponse().getSeatBid())
                .extracting(BidderSeatBid::getBids, BidderSeatBid::getErrors)
                .containsOnly(tuple(singletonList(expectedBidderBid), singletonList(expectedError)));
    }

    @Test
    public void shouldUpdateBidPriceWithCurrencyConversionForMultipleBid() {
        // given
        final BigDecimal bidder1Price = BigDecimal.valueOf(1.5);
        final BigDecimal bidder2Price = BigDecimal.valueOf(2);
        final BigDecimal bidder3Price = BigDecimal.valueOf(3);
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().price(bidder1Price).build(), "EUR"))));
        givenBidder("bidder2", mock(Bidder.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().price(bidder2Price).build(), "GBP"))));
        givenBidder("bidder3", mock(Bidder.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().price(bidder3Price).build(), "USD"))));

        final Map<String, Integer> impBidders = new HashMap<>();
        impBidders.put("bidder1", 1);
        impBidders.put("bidder2", 2);
        impBidders.put("bidder3", 3);
        final BidRequest bidRequest = givenBidRequest(
                singletonList(givenImp(impBidders, identity())), builder -> builder.cur(singletonList("USD")));
        final List<AuctionParticipation> participations = toParticipations(
                Arrays.asList("bidder1", "bidder2", "bidder3"),
                Arrays.asList(bidRequest, bidRequest, bidRequest));

        final BigDecimal updatedPrice = BigDecimal.valueOf(10.0);
        given(currencyService.convertCurrency(any(), any(), any(), any(), any())).willReturn(updatedPrice);
        given(currencyService.convertCurrency(any(), any(), any(), eq("USD"), any())).willReturn(bidder3Price);

        // when
        final List<AuctionParticipation> result = target.waitForBidResponses(participations, timeout, false, false,
                bidderAliases, bidAdjustment, null, false).result();

        // then
        verify(currencyService).convertCurrency(eq(bidder1Price), eq(null), eq("USD"), eq("EUR"), eq(false));
        verify(currencyService).convertCurrency(eq(bidder2Price), eq(null), eq("USD"), eq("GBP"), eq(false));
        verify(currencyService).convertCurrency(eq(bidder3Price), eq(null), eq("USD"), eq("USD"), eq(false));
        verifyNoMoreInteractions(currencyService);

        assertThat(result).hasSize(3)
                .flatExtracting(participation -> participation.getBidderResponse().getSeatBid().getBids())
                .extracting(bidderBid -> bidderBid.getBid().getPrice())
                .containsOnly(bidder3Price, updatedPrice, updatedPrice);
    }

    private static List<AuctionParticipation> toParticipations(List<String> bidderNames, List<BidRequest> bidRequests) {
        final ArrayList<AuctionParticipation> auctionParticipations = new ArrayList<>();
        for (int i = 0; i < bidderNames.size(); i++) {
            final String bidder = bidderNames.get(i);
            auctionParticipations.add(AuctionParticipation.builder()
                    .bidder(bidder)
                    .bidderRequest(BidderRequest.of(bidder, bidRequests.get(i)))
                    .build());
        }
        return auctionParticipations;
    }

    private static <K, V> Map<K, V> doubleMap(K key1, V value1, K key2, V value2) {
        final Map<K, V> map = new HashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }

    private void givenBidder(BidderSeatBid response) {
        given(httpBidderRequester.requestBids(any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(response));
    }

    private void givenBidder(String bidderName, Bidder<?> bidder, BidderSeatBid response) {
        doReturn(bidder).when(bidderCatalog).bidderByName(eq(bidderName));
        given(httpBidderRequester.requestBids(same(bidder), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(response));
    }

    private static BidRequest givenBidRequest(
            List<Imp> imp,
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestBuilderCustomizer) {
        return bidRequestBuilderCustomizer.apply(BidRequest.builder().cur(singletonList("USD")).imp(imp)).build();
    }

    private static BidRequest givenBidRequest(List<Imp> imp) {
        return givenBidRequest(imp, identity());
    }

    private static <T> Imp givenImp(T ext, Function<Imp.ImpBuilder, Imp.ImpBuilder> impBuilderCustomizer) {
        return impBuilderCustomizer.apply(Imp.builder()
                .ext(ext != null ? mapper.valueToTree(ext) : null))
                .build();
    }

    private static <T> List<Imp> givenSingleImp(T ext) {
        return singletonList(givenImp(ext, identity()));
    }

    private static BidderSeatBid givenSeatBid(List<BidderBid> bids) {
        return BidderSeatBid.of(bids, emptyList(), emptyList());
    }

    private static BidderBid givenBid(Bid bid) {
        return BidderBid.of(bid, banner, null);
    }

    private static BidderBid givenBid(Bid bid, String cur) {
        return BidderBid.of(bid, banner, cur);
    }
}

