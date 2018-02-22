package org.prebid.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BidRequest.BidRequestBuilder;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Imp.ImpBuilder;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import org.assertj.core.data.Offset;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.VertxTest;
import org.prebid.bidder.BidderRequester;
import org.prebid.bidder.model.BidderBid;
import org.prebid.bidder.model.BidderError;
import org.prebid.bidder.model.BidderSeatBid;
import org.prebid.cache.CacheService;
import org.prebid.cookie.UidsCookie;
import org.prebid.exception.PreBidException;
import org.prebid.execution.GlobalTimeout;
import org.prebid.metric.AdapterMetrics;
import org.prebid.metric.MetricName;
import org.prebid.metric.Metrics;
import org.prebid.model.openrtb.ext.ExtPrebid;
import org.prebid.model.openrtb.ext.request.ExtBidRequest;
import org.prebid.model.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.model.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.model.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.model.openrtb.ext.response.ExtBidPrebid;
import org.prebid.model.openrtb.ext.response.ExtBidResponse;
import org.prebid.model.openrtb.ext.response.ExtHttpCall;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static java.util.function.Function.identity;
import static org.apache.commons.lang3.exception.ExceptionUtils.rethrow;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.*;
import static org.prebid.model.openrtb.ext.response.BidType.banner;

public class ExchangeServiceTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderRequesterCatalog bidderRequesterCatalog;
    @Mock
    private CacheService cacheService;
    @Mock
    private Metrics metrics;
    @Mock
    private AdapterMetrics adapterMetrics;

    private ExchangeService exchangeService;

    @Mock
    private UidsCookie uidsCookie;
    @Mock
    private BidderRequester bidderRequester;

    @Before
    public void setUp() {
        given(bidderRequesterCatalog.byName(anyString())).willReturn(bidderRequester);
        given(bidderRequester.cookieFamilyName()).willReturn("cookieFamily");
        given(metrics.forAdapter(anyString())).willReturn(adapterMetrics);

        exchangeService = new ExchangeService(bidderRequesterCatalog, cacheService, metrics, 0);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(
                () -> new ExchangeService(null, cacheService, metrics, 0));
        assertThatNullPointerException().isThrownBy(
                () -> new ExchangeService(bidderRequesterCatalog, null, metrics, 0));
        assertThatNullPointerException().isThrownBy(
                () -> new ExchangeService(bidderRequesterCatalog, cacheService, null, 0));
    }

    @Test
    public void creationShouldFailOnNegativeExpectedCacheTime() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ExchangeService(bidderRequesterCatalog, cacheService, metrics, -1));
    }

    @Test
    public void shouldTolerateImpWithoutExtension() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(null));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout()).result();

        // then
        verifyZeroInteractions(bidderRequesterCatalog);
        verifyZeroInteractions(bidderRequester);
        assertThat(bidResponse).isNotNull();
    }

    @Test
    public void shouldTolerateImpWithUnknownBidderInExtension() {
        // given
        given(bidderRequesterCatalog.isValidName(anyString())).willReturn(false);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("invalid", 0)));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout()).result();

        // then
        verify(bidderRequesterCatalog).isValidName(eq("invalid"));
        verifyNoMoreInteractions(bidderRequesterCatalog);
        verifyZeroInteractions(bidderRequester);
        assertThat(bidResponse).isNotNull();
    }

    @Test
    public void shouldTolerateMissingPrebidImpExtension() {
        // given
        givenHttpConnector(givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout());

        // then
        final BidRequest capturedBidRequest = captureBidRequest();
        assertThat(capturedBidRequest.getImp()).hasSize(1)
                .element(0).returns(mapper.valueToTree(ExtPrebid.of(null, 1)), Imp::getExt);
    }

    @Test
    public void shouldExtractRequestWithBidderSpecificExtension() {
        // given
        givenHttpConnector(givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(singletonList(
                givenImp(doubleMap("prebid", 0, "someBidder", 1), builder -> builder
                        .id("impId")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(300).build()))
                                .build()))),
                builder -> builder.id("requestId").tmax(500L));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout());

        // then
        final BidRequest capturedBidRequest = captureBidRequest();
        assertThat(capturedBidRequest).isEqualTo(BidRequest.builder()
                .id("requestId")
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(300).build()))
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(0, 1)))
                        .build()))
                .tmax(500L)
                .build());
    }

    @Test
    public void shouldExtractMultipleRequests() {
        // given
        final BidderRequester bidderRequester1 = mock(BidderRequester.class);
        final BidderRequester bidderRequester2 = mock(BidderRequester.class);
        givenHttpConnector("bidder1", bidderRequester1, givenEmptySeatBid());
        givenHttpConnector("bidder2", bidderRequester2, givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(asList(
                givenImp(doubleMap("bidder1", 1, "bidder2", 2), identity()),
                givenImp(singletonMap("bidder1", 3), identity())));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout());

        // then
        final ArgumentCaptor<BidRequest> bidRequest1Captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(bidderRequester1).requestBids(bidRequest1Captor.capture(), any());
        final BidRequest capturedBidRequest1 = bidRequest1Captor.getValue();
        assertThat(capturedBidRequest1.getImp()).hasSize(2)
                .extracting(imp -> imp.getExt().get("bidder").asInt())
                .containsOnly(1, 3);

        final ArgumentCaptor<BidRequest> bidRequest2Captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(bidderRequester2).requestBids(bidRequest2Captor.capture(), any());
        final BidRequest capturedBidRequest2 = bidRequest2Captor.getValue();
        assertThat(capturedBidRequest2.getImp()).hasSize(1)
                .element(0).returns(2, imp -> imp.getExt().get("bidder").asInt());
    }

    @Test
    public void shouldSpecifyNbrInResponseIfNoValidBidders() {
        // given
        final BidRequest bidRequest = givenBidRequest(emptyList());

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout()).result();

        // then
        assertThat(bidResponse).returns(2, BidResponse::getNbr);
    }

    @Test
    public void shouldTolerateBidderResultWithoutBids() {
        // given
        givenHttpConnector(givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout()).result();

        // then
        assertThat(bidResponse.getSeatbid()).isEmpty();
    }

    @Test
    public void shouldReturnPopulatedSeatBid() {
        // given
        givenHttpConnector(BidderSeatBid.of(
                singletonList(BidderBid.of(
                        Bid.builder().id("bidId").price(TEN)
                                .ext(mapper.valueToTree(singletonMap("bidExt", 1))).build(), banner)),
                mapper.valueToTree(singletonMap("seatBidExt", 2)),
                emptyList(),
                emptyList()));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout()).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1).element(0).isEqualTo(SeatBid.builder()
                .seat("someBidder")
                .group(0)
                .bid(singletonList(Bid.builder()
                        .id("bidId")
                        .price(TEN)
                        .ext(mapper.valueToTree(
                                ExtPrebid.of(ExtBidPrebid.of(banner, null, null), singletonMap("bidExt", 1))))
                        .build()))
                .ext(mapper.valueToTree(ExtPrebid.of(null, singletonMap("seatBidExt", 2))))
                .build());
    }

    @Test
    public void shouldTolerateMissingExtInSeatBidAndBid() {
        // given
        givenHttpConnector(givenSingleSeatBid(BidderBid.of(Bid.builder().price(ONE).id("bidId").build(), banner)));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout()).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1).element(0).isEqualTo(SeatBid.builder()
                .seat("someBidder")
                .group(0)
                .bid(singletonList(Bid.builder()
                        .id("bidId")
                        .price(ONE)
                        .ext(mapper.valueToTree(ExtPrebid.of(ExtBidPrebid.of(banner, null, null), null)))
                        .build()))
                .build());
    }

    @Test
    public void shouldReturnMultipleSeatBids() {
        // given
        given(bidderRequesterCatalog.isValidName(anyString())).willReturn(true);
        given(bidderRequester.requestBids(any(), any()))
                .willReturn(Future.succeededFuture(givenSeatBid(asList(
                        givenBid(Bid.builder().price(TEN).build()),
                        givenBid(Bid.builder().price(TEN).build())))))
                .willReturn(Future.succeededFuture(givenSingleSeatBid(
                        givenBid(Bid.builder().price(ONE).build()))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(doubleMap("bidder1", 1, "bidder2", 2)));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout()).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(2)
                .extracting(seatBid -> seatBid.getBid().size()).containsOnly(2, 1);
    }

    @Test
    public void shouldPopulateBidResponseExtension() throws JsonProcessingException {
        // given
        givenHttpConnector("bidder1", mock(BidderRequester.class), BidderSeatBid.of(
                singletonList(givenBid(Bid.builder().price(ONE).build())), null, emptyList(),
                singletonList(BidderError.create("bidder1_error1"))));
        givenHttpConnector("bidder2", mock(BidderRequester.class), BidderSeatBid.of(
                singletonList(givenBid(Bid.builder().price(ONE).build())), null, emptyList(),
                asList(BidderError.create("bidder2_error1"), BidderError.create("bidder2_error2"))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(doubleMap("bidder1", 1, "bidder2", 2)));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout()).result();

        // then
        final ExtBidResponse ext = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);
        assertThat(ext.getResponsetimemillis()).hasSize(2).containsOnlyKeys("bidder1", "bidder2");
        assertThat(ext.getErrors()).hasSize(2).containsOnly(
                entry("bidder1", singletonList("bidder1_error1")),
                entry("bidder2", asList("bidder2_error1", "bidder2_error2")));
    }

    @Test
    public void shouldPopulateBidResponseDebugExtensionIfTestFlagIsTrue() throws JsonProcessingException {
        // given
        givenHttpConnector("bidder1", mock(BidderRequester.class), BidderSeatBid.of(
                singletonList(givenBid(Bid.builder().price(ONE).build())), null,
                singletonList(ExtHttpCall.builder()
                        .uri("bidder1_uri1")
                        .requestbody("bidder1_requestBody1")
                        .status(200)
                        .responsebody("bidder1_responseBody1")
                        .build()),
                emptyList()));
        givenHttpConnector("bidder2", mock(BidderRequester.class), BidderSeatBid.of(
                singletonList(givenBid(Bid.builder().price(ONE).build())), null,
                asList(
                        ExtHttpCall.builder()
                                .uri("bidder2_uri1")
                                .requestbody("bidder2_requestBody1")
                                .status(200)
                                .responsebody("bidder2_responseBody1")
                                .build(),
                        ExtHttpCall.builder()
                                .uri("bidder2_uri2")
                                .requestbody("bidder2_requestBody2")
                                .status(404)
                                .responsebody("bidder2_responseBody2")
                                .build()),
                emptyList()));

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(doubleMap("bidder1", 1, "bidder2", 2)),
                builder -> builder.test(1));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout()).result();

        // then
        final ExtBidResponse ext = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);
        assertThat(ext.getDebug()).isNotNull();
        assertThat(ext.getDebug().getHttpcalls()).hasSize(2).containsOnly(
                entry("bidder1", singletonList(ExtHttpCall.builder()
                        .uri("bidder1_uri1")
                        .requestbody("bidder1_requestBody1")
                        .status(200)
                        .responsebody("bidder1_responseBody1")
                        .build())),
                entry("bidder2", asList(
                        ExtHttpCall.builder()
                                .uri("bidder2_uri1")
                                .requestbody("bidder2_requestBody1")
                                .status(200)
                                .responsebody("bidder2_responseBody1")
                                .build(),
                        ExtHttpCall.builder()
                                .uri("bidder2_uri2")
                                .requestbody("bidder2_requestBody2")
                                .status(404)
                                .responsebody("bidder2_responseBody2")
                                .build())));
    }

    @Test
    public void shouldNotPopulateBidResponseDebugExtensionIfTestFlagIsFalse() throws JsonProcessingException {
        // given
        givenHttpConnector(BidderSeatBid.of(
                singletonList(givenBid(Bid.builder().price(ONE).build())), null,
                singletonList(ExtHttpCall.builder()
                        .uri("bidder1_uri1")
                        .requestbody("bidder1_requestBody1")
                        .status(200)
                        .responsebody("bidder1_responseBody1")
                        .build()),
                emptyList()));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder1", 1)));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout()).result();

        // then
        final ExtBidResponse ext = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);
        assertThat(ext.getDebug()).isNull();
    }

    @Test
    public void shouldReturnErrorIfRequestExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(emptyList(),
                builder -> builder.ext(mapper.valueToTree(singletonMap("prebid", 1))));

        // when
        final Future<BidResponse> result = exchangeService.holdAuction(bidRequest, uidsCookie, timeout());

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class)
                .hasMessageStartingWith("Error decoding bidRequest.ext: ");
    }

    @Test
    public void shouldTolerateNullRequestExtPrebid() {
        // given
        givenHttpConnector(givenSingleSeatBid(givenBid(Bid.builder().price(ONE).build())));

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.ext(mapper.valueToTree(singletonMap("someField", 1))));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout()).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(bid -> toExtPrebid(bid.getExt()).getPrebid().getTargeting())
                .allSatisfy(map -> assertThat(map).isNull());
    }

    @Test
    public void shouldTolerateNullRequestExtPrebidTargeting() {
        // given
        givenHttpConnector(givenSingleSeatBid(givenBid(Bid.builder().price(ONE).build())));

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.ext(mapper.valueToTree(singletonMap("prebid", singletonMap("someField", 1)))));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout()).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(bid -> toExtPrebid(bid.getExt()).getPrebid().getTargeting())
                .allSatisfy(map -> assertThat(map).isNull());
    }

    @Test
    public void shouldTolerateEmptyRequestExtPrebidTargeting() throws JsonProcessingException {
        // given
        givenHttpConnector(givenSingleSeatBid(givenBid(Bid.builder().impid("impId1").price(ONE).build())));

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                        ExtRequestTargeting.of(null), null, null)))));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout()).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(bid -> toExtPrebid(bid.getExt()).getPrebid().getTargeting())
                .allSatisfy(map -> assertThat(map).isNotEmpty());
        assertThat(mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class).getErrors())
                .containsOnly(entry("someBidder", emptyList()));
    }

    @Test
    public void shouldTolerateInvalidPriceGranularity() throws JsonProcessingException {
        // given
        givenHttpConnector(givenSingleSeatBid(givenBid(Bid.builder().impid("impId1").price(BigDecimal.ONE).build())));

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                        ExtRequestTargeting.of("invalid"), null, null)))));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout()).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(bid -> toExtPrebid(bid.getExt()).getPrebid().getTargeting())
                .allSatisfy(map -> assertThat(map).isNotEmpty());
        assertThat(mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class).getErrors()).containsOnly(entry(
                "someBidder",
                singletonList("Price bucket granularity error: 'invalid' is not a recognized granularity")));
    }

    @Test
    public void shouldPopulateTargetingKeywords() {
        // given
        givenHttpConnector("bidder1", mock(BidderRequester.class), givenSeatBid(asList(
                givenBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build()),
                givenBid(Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.valueOf(6.35)).build()))));
        givenHttpConnector("bidder2", mock(BidderRequester.class), givenSeatBid(asList(
                givenBid(Bid.builder().id("bidId3").impid("impId1").price(BigDecimal.valueOf(7.19)).build()),
                givenBid(Bid.builder().id("bidId4").impid("impId2").price(BigDecimal.valueOf(4.99)).build()))));

        final BidRequest bidRequest = givenBidRequest(asList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(doubleMap("bidder1", 1, "bidder2", 2), builder -> builder.id("impId1")),
                givenImp(doubleMap("bidder1", 1, "bidder2", 2), builder -> builder.id("impId2"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                        ExtRequestTargeting.of("low"), null, null)))));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout()).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(
                        Bid::getId,
                        bid -> toExtPrebid(bid.getExt()).getPrebid().getTargeting().get("hb_bidder"))
                .containsOnly(
                        tuple("bidId1", null),
                        tuple("bidId2", "bidder1"),
                        tuple("bidId3", "bidder2"),
                        tuple("bidId4", null));
    }

    @Test
    public void shouldPopulateCacheIdTargetingKeywords() {
        // given
        givenHttpConnector("bidder1", mock(BidderRequester.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build()))));
        givenHttpConnector("bidder2", mock(BidderRequester.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().id("bidId2").impid("impId1").price(BigDecimal.valueOf(7.19)).build()))));

        given(cacheService.cacheBidsOpenrtb(anyList(), any()))
                .willReturn(Future.succeededFuture(singletonList("cacheId2")));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(doubleMap("bidder1", 1, "bidder2", 2), builder -> builder.id("impId1"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                        ExtRequestTargeting.of("low"), null,
                        ExtRequestPrebidCache.of(mapper.createObjectNode()))))));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout()).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(bid -> toExtPrebid(bid.getExt()).getPrebid().getTargeting())
                .extracting(
                        targeting -> targeting.get("hb_bidder"),
                        targeting -> targeting.get("hb_cache_id_bidder2"),
                        targeting -> targeting.get("hb_cache_id"))
                .containsOnly(
                        tuple(null, null, null),
                        tuple("bidder2", "cacheId2", "cacheId2"));
    }

    @Test
    public void shouldNotPopulateCacheIdTargetingKeywordsIfCacheServiceReturnEmptyResult() {
        // given
        givenHttpConnector("bidder1", mock(BidderRequester.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build()))));
        givenHttpConnector("bidder2", mock(BidderRequester.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().id("bidId2").impid("impId1").price(BigDecimal.valueOf(7.19)).build()))));

        given(cacheService.cacheBidsOpenrtb(anyList(), any()))
                .willReturn(Future.succeededFuture(emptyList()));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(doubleMap("bidder1", 1, "bidder2", 2), builder -> builder.id("impId1"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                        ExtRequestTargeting.of("low"), null,
                        ExtRequestPrebidCache.of(mapper.createObjectNode()))))));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout()).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(bid -> toExtPrebid(bid.getExt()).getPrebid().getTargeting())
                .extracting(
                        targeting -> targeting.get("hb_bidder"),
                        targeting -> targeting.get("hb_cache_id_bidder2"),
                        targeting -> targeting.get("hb_cache_id"))
                .containsOnly(
                        tuple(null, null, null),
                        tuple("bidder2", null, null));
    }

    @Test
    public void shouldNotPopulateCacheIdTargetingKeywordsIfBidCpmIsZero() {
        // given
        givenHttpConnector("bidder1", mock(BidderRequester.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.ZERO).build()))));
        givenHttpConnector("bidder2", mock(BidderRequester.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.valueOf(5.67)).build()))));

        given(cacheService.cacheBidsOpenrtb(anyList(), any()))
                .willReturn(Future.succeededFuture(singletonList("cacheId2")));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(doubleMap("bidder1", 1, "bidder2", 2), builder -> builder.id("impId2"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                        ExtRequestTargeting.of("low"), null,
                        ExtRequestPrebidCache.of(mapper.createObjectNode()))))));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout()).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(bid -> toExtPrebid(bid.getExt()).getPrebid().getTargeting())
                .extracting(
                        targeting -> targeting.get("hb_bidder"),
                        targeting -> targeting.get("hb_cache_id_bidder2"),
                        targeting -> targeting.get("hb_cache_id"))
                .containsOnly(
                        tuple("bidder1", null, null),
                        tuple("bidder2", "cacheId2", "cacheId2"));
    }

    @Test
    public void shouldNotModifyUserFromRequestIfNoBuyeridInCookie() {
        // given
        givenHttpConnector(givenEmptySeatBid());

        // this is not required but stated for clarity's sake
        given(uidsCookie.uidFrom(anyString())).willReturn(null);

        final User user = User.builder().id("userId").build();
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.user(user));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout());

        // then
        final BidRequest capturedBidRequest = captureBidRequest();
        assertThat(capturedBidRequest.getUser()).isSameAs(user);
    }

    @Test
    public void shouldHonorBuyeridFromRequest() {
        // given
        givenHttpConnector(givenEmptySeatBid());

        given(uidsCookie.uidFrom(anyString())).willReturn("buyeridFromCookie");

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.user(User.builder().buyeruid("buyeridFromRequest").build()));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout());

        // then
        final BidRequest capturedBidRequest = captureBidRequest();
        assertThat(capturedBidRequest.getUser()).isEqualTo(User.builder().buyeruid("buyeridFromRequest").build());
    }

    @Test
    public void shouldAddBuyeridToUserFromRequest() {
        // given
        givenHttpConnector(givenEmptySeatBid());
        given(uidsCookie.uidFrom(eq("cookieFamily"))).willReturn("buyerid");

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.user(User.builder().id("userId").build()));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout());

        // then
        final BidRequest capturedBidRequest = captureBidRequest();
        assertThat(capturedBidRequest.getUser()).isEqualTo(User.builder().id("userId").buyeruid("buyerid").build());
    }

    @Test
    public void shouldCreateUserIfMissingInRequestAndBuyeridPresentInCookie() {
        // given
        givenHttpConnector(givenEmptySeatBid());

        given(uidsCookie.uidFrom(eq("cookieFamily"))).willReturn("buyerid");

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout());

        // then
        final BidRequest capturedBidRequest = captureBidRequest();
        assertThat(capturedBidRequest.getUser()).isEqualTo(User.builder().buyeruid("buyerid").build());
    }

    @Test
    public void shouldPassGlobalTimeoutToConnectorUnchangedIfCachingIsNotRequested() {
        // given
        givenHttpConnector(givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        final GlobalTimeout timeout = GlobalTimeout.create(500);

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout);

        // then
        verify(bidderRequester).requestBids(any(), same(timeout));
    }

    @Test
    public void shouldPassReducedGlobalTimeoutToConnectorAndOriginalToCacheServiceIfCachingIsRequested() {
        // given
        exchangeService = new ExchangeService(bidderRequesterCatalog, cacheService, metrics, 100);

        givenHttpConnector(givenSeatBid(singletonList(
                givenBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build()))));

        given(cacheService.cacheBidsOpenrtb(anyList(), any()))
                .willReturn(Future.succeededFuture(singletonList("cacheId1")));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                        ExtRequestTargeting.of("low"), null,
                        ExtRequestPrebidCache.of(mapper.createObjectNode()))))));

        final GlobalTimeout timeout = GlobalTimeout.create(500);

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout).result();

        // then
        final ArgumentCaptor<GlobalTimeout> timeoutCaptor = ArgumentCaptor.forClass(GlobalTimeout.class);
        verify(bidderRequester).requestBids(any(), timeoutCaptor.capture());
        assertThat(timeoutCaptor.getValue().remaining()).isCloseTo(400L, Offset.offset(20L));
        verify(cacheService).cacheBidsOpenrtb(anyList(), same(timeout));
    }

    @Test
    public void shouldIncrementRequestAndNoCookieAndUpdatePriceAndRequestTimeMetrics() {
        // given
        given(bidderRequesterCatalog.isValidName(anyString())).willReturn(true);

        given(bidderRequester.requestBids(any(), any()))
                .willReturn(Future.succeededFuture(givenSeatBid(singletonList(
                        givenBid(Bid.builder().price(TEN).build())))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("somebidder", 1)));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, GlobalTimeout.create(500));

        // then
        verify(adapterMetrics).incCounter(eq(MetricName.requests));
        verify(adapterMetrics).incCounter(eq(MetricName.no_cookie_requests));
        verify(adapterMetrics).updateTimer(eq(MetricName.request_time), anyLong());
        verify(adapterMetrics, times(1)).updateHistogram(eq(MetricName.prices), anyLong());
    }

    @Test
    public void shouldUpdatePriceMetricWithZeroValueIfBidPriceNull() {
        // given
        given(bidderRequesterCatalog.isValidName(anyString())).willReturn(true);

        given(bidderRequester.requestBids(any(), any()))
                .willReturn(Future.succeededFuture(givenSeatBid(singletonList(
                        givenBid(Bid.builder().price(null).build())))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("somebidder", 1)));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, GlobalTimeout.create(500));

        // then
        verify(adapterMetrics, times(1)).updateHistogram(eq(MetricName.prices), eq(0L));
    }

    @Test
    public void shouldIncrementNoBidRequestsMetric() {
        // given
        given(bidderRequesterCatalog.isValidName(anyString())).willReturn(true);

        given(bidderRequester.requestBids(any(), any()))
                .willReturn(Future.succeededFuture(givenSeatBid(emptyList())));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("somebidder", 1)));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, GlobalTimeout.create(500));

        // then
        verify(adapterMetrics).incCounter(eq(MetricName.no_bid_requests));
    }

    @Test
    public void shouldIncrementErrorMetrics() {
        // given
        given(bidderRequesterCatalog.isValidName(anyString())).willReturn(true);

        given(bidderRequester.requestBids(any(), any()))
                .willReturn(Future.succeededFuture(BidderSeatBid.of(emptyList(), null, emptyList(), asList(
                        BidderError.of("bidder-error-1", false),
                        BidderError.of("bidder-error-2", false),
                        BidderError.of("bidder-error-timeout-1", true)
                ))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("somebidder", 1)));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, GlobalTimeout.create(500));

        // then
        verify(adapterMetrics, times(1)).incCounter(eq(MetricName.timeout_requests));
        verify(adapterMetrics, times(2)).incCounter(eq(MetricName.error_requests));
    }

    private static GlobalTimeout timeout() {
        return GlobalTimeout.create(500);
    }

    private BidRequest captureBidRequest() {
        final ArgumentCaptor<BidRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidRequest.class);
        verify(bidderRequester).requestBids(bidRequestCaptor.capture(), any());
        return bidRequestCaptor.getValue();
    }

    private static BidRequest givenBidRequest(
            List<Imp> imp, Function<BidRequestBuilder, BidRequestBuilder> bidRequestBuilderCustomizer) {
        return bidRequestBuilderCustomizer.apply(BidRequest.builder().imp(imp)).build();
    }

    private static BidRequest givenBidRequest(List<Imp> imp) {
        return givenBidRequest(imp, identity());
    }

    private static <T> Imp givenImp(T ext, Function<ImpBuilder, ImpBuilder> impBuilderCustomizer) {
        return impBuilderCustomizer.apply(Imp.builder().ext(mapper.valueToTree(ext))).build();
    }

    private static <T> List<Imp> givenSingleImp(T ext) {
        return singletonList(givenImp(ext, identity()));
    }

    private void givenHttpConnector(BidderSeatBid response) {
        given(bidderRequesterCatalog.isValidName(anyString())).willReturn(true);
        given(bidderRequester.requestBids(any(), any())).willReturn(Future.succeededFuture(response));
    }

    private void givenHttpConnector(String bidderRequesterName, BidderRequester bidderRequester,
                                    BidderSeatBid response) {
        given(bidderRequesterCatalog.isValidName(eq(bidderRequesterName))).willReturn(true);
        given(bidderRequesterCatalog.byName(eq(bidderRequesterName))).willReturn(bidderRequester);
        given(bidderRequester.requestBids(any(), any())).willReturn(Future.succeededFuture(response));
    }

    private static BidderSeatBid givenSeatBid(List<BidderBid> bids) {
        return BidderSeatBid.of(bids, null, emptyList(), emptyList());
    }

    private static BidderSeatBid givenSingleSeatBid(BidderBid bid) {
        return givenSeatBid(singletonList(bid));
    }

    private static BidderSeatBid givenEmptySeatBid() {
        return givenSeatBid(emptyList());
    }

    private static BidderBid givenBid(Bid bid) {
        return BidderBid.of(bid, null);
    }

    private static <K, V> Map<K, V> doubleMap(K key1, V value1, K key2, V value2) {
        final Map<K, V> map = new HashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }

    private static ExtPrebid<ExtBidPrebid, ?> toExtPrebid(ObjectNode ext) {
        try {
            return mapper.readValue(mapper.treeAsTokens(ext), new TypeReference<ExtPrebid<ExtBidPrebid, ?>>() {
            });
        } catch (IOException e) {
            return rethrow(e);
        }
    }
}
