package org.prebid.server.auction;

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
import io.vertx.core.json.Json;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderRequester;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.AdapterMetrics;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularityBucket;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.validation.ResponseBidValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;

public class ExchangeServiceTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private ResponseBidValidator responseBidValidator;
    @Mock
    private Usersyncer usersyncer;
    @Mock
    private CacheService cacheService;
    @Mock
    private Metrics metrics;
    @Mock
    private AdapterMetrics adapterMetrics;
    @Mock
    private UidsCookie uidsCookie;
    @Mock
    private BidderRequester bidderRequester;
    private Clock clock;

    private ExchangeService exchangeService;

    private Timeout timeout;

    @Before
    public void setUp() {
        given(bidderCatalog.isValidName(anyString())).willReturn(true);
        given(bidderCatalog.isActive(anyString())).willReturn(true);
        given(bidderCatalog.bidderRequesterByName(anyString())).willReturn(bidderRequester);
        given(bidderCatalog.usersyncerByName(anyString())).willReturn(usersyncer);

        given(responseBidValidator.validate(any())).willReturn(ValidationResult.success());
        given(usersyncer.cookieFamilyName()).willReturn("cookieFamily");
        given(metrics.forAdapter(anyString())).willReturn(adapterMetrics);

        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        timeout = new TimeoutFactory(clock).create(500);

        exchangeService = new ExchangeService(bidderCatalog, responseBidValidator, cacheService, metrics, clock, 0);
    }

    @Test
    public void creationShouldFailOnNegativeExpectedCacheTime() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ExchangeService(bidderCatalog, responseBidValidator, cacheService, metrics, clock, -1));
    }

    @Test
    public void shouldTolerateImpWithoutExtension() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(null));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout).result();

        // then
        verifyZeroInteractions(bidderCatalog);
        verifyZeroInteractions(bidderRequester);
        assertThat(bidResponse).isNotNull();
    }

    @Test
    public void shouldTolerateImpWithUnknownBidderInExtension() {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(false);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("invalid", 0)));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout).result();

        // then
        verify(bidderCatalog).isValidName(eq("invalid"));
        verifyNoMoreInteractions(bidderCatalog);
        verifyZeroInteractions(bidderRequester);
        assertThat(bidResponse).isNotNull();
    }

    @Test
    public void shouldTolerateMissingPrebidImpExtension() {
        // given
        givenHttpConnector(givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout);

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
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout);

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
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout);

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
    public void shouldExtractRequestByAliasForCorrectBidder() {
        // given
        final BidderRequester bidderRequester = mock(BidderRequester.class);
        givenHttpConnector("bidder", bidderRequester, givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(singletonList(
                givenImp(singletonMap("bidderAlias", 1), identity())),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                        singletonMap("bidderAlias", "bidder"), null, null, null, null)))));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout);

        // then
        final ArgumentCaptor<BidRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidRequest.class);
        verify(bidderRequester).requestBids(bidRequestCaptor.capture(), any());
        assertThat(bidRequestCaptor.getValue().getImp()).hasSize(1)
                .extracting(imp -> imp.getExt().get("bidder").asInt())
                .contains(1);
    }

    @Test
    public void shouldExtractMultipleRequestsForTheSameBidderIfAliasesWasUsed() {
        // given
        final BidderRequester bidderRequester = mock(BidderRequester.class);
        givenHttpConnector("bidder", bidderRequester, givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(singletonList(
                givenImp(doubleMap("bidder", 1, "bidderAlias", 2), identity())),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                        singletonMap("bidderAlias", "bidder"), null, null, null, null)))));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout);

        // then
        final ArgumentCaptor<BidRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidRequest.class);
        verify(bidderRequester, times(2)).requestBids(bidRequestCaptor.capture(), any());
        final List<BidRequest> capturedBidRequests = bidRequestCaptor.getAllValues();

        assertThat(capturedBidRequests).hasSize(2)
                .extracting(capturedBidRequest -> capturedBidRequest.getImp().get(0).getExt().get("bidder").asInt())
                .containsOnly(2, 1);
    }

    @Test
    public void shouldSpecifyNbrInResponseIfNoValidBidders() {
        // given
        final BidRequest bidRequest = givenBidRequest(emptyList());

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout).result();

        // then
        assertThat(bidResponse).returns(2, BidResponse::getNbr);
    }

    @Test
    public void shouldTolerateBidderResultWithoutBids() {
        // given
        givenHttpConnector(givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout).result();

        // then
        assertThat(bidResponse.getSeatbid()).isEmpty();
    }

    @Test
    public void shouldReturnPopulatedSeatBid() {
        // given
        givenHttpConnector(BidderSeatBid.of(
                singletonList(BidderBid.of(
                        Bid.builder().id("bidId")
                                .ext(mapper.valueToTree(singletonMap("bidExt", 1))).build(), banner)),
                emptyList(),
                emptyList()));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1).element(0).isEqualTo(SeatBid.builder()
                .seat("someBidder")
                .group(0)
                .bid(singletonList(Bid.builder()
                        .id("bidId")
                        .ext(mapper.valueToTree(
                                ExtPrebid.of(ExtBidPrebid.of(banner, null), singletonMap("bidExt", 1))))
                        .build()))
                .build());
    }

    @Test
    public void shouldTolerateMissingExtInSeatBidAndBid() {
        // given
        givenHttpConnector(givenSingleSeatBid(BidderBid.of(Bid.builder().id("bidId").build(), banner)));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1).element(0).isEqualTo(SeatBid.builder()
                .seat("someBidder")
                .group(0)
                .bid(singletonList(Bid.builder()
                        .id("bidId")
                        .ext(mapper.valueToTree(ExtPrebid.of(ExtBidPrebid.of(banner, null), null)))
                        .build()))
                .build());
    }

    @Test
    public void shouldReturnMultipleSeatBids() {
        // given
        given(bidderRequester.requestBids(any(), any()))
                .willReturn(Future.succeededFuture(givenSeatBid(asList(
                        givenBid(Bid.builder().build()),
                        givenBid(Bid.builder().build())))))
                .willReturn(Future.succeededFuture(givenSingleSeatBid(
                        givenBid(Bid.builder().build()))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(doubleMap("bidder1", 1, "bidder2", 2)));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(2)
                .extracting(seatBid -> seatBid.getBid().size()).containsOnly(2, 1);
    }

    @Test
    public void shouldReturnSeparateSeatBidsForTheSameBidderIfBiddersAliasAndBidderWereUsedWithingSingleImp() {
        // given
        given(bidderCatalog.isValidName("bidder")).willReturn(true);

        given(bidderRequester.requestBids(eq(givenBidRequest(givenSingleImp(singletonMap("bidder", 1)),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                        singletonMap("bidderAlias", "bidder"), null, null, null, null)))))), any()))
                .willReturn(Future.succeededFuture(givenSeatBid(singletonList(
                        givenBid(Bid.builder().build())))));

        given(bidderRequester.requestBids(eq(givenBidRequest(givenSingleImp(singletonMap("bidder", 2)),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                        singletonMap("bidderAlias", "bidder"), null, null, null, null)))))), any()))
                .willReturn(Future.succeededFuture(givenSeatBid(singletonList(
                        givenBid(Bid.builder().build())))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(doubleMap("bidder", 1, "bidderAlias", 2)),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                        singletonMap("bidderAlias", "bidder"), null, null, null, null)))));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout).result();

        // then
        verify(bidderRequester, times(2)).requestBids(any(), any());
        assertThat(bidResponse.getSeatbid()).hasSize(2)
                .extracting(seatBid -> seatBid.getBid().size()).containsOnly(1, 1);
    }

    @Test
    public void shouldPopulateBidResponseExtension() throws JsonProcessingException {
        // given
        givenHttpConnector("bidder1", mock(BidderRequester.class), BidderSeatBid.of(
                singletonList(givenBid(Bid.builder().build())), emptyList(),
                singletonList(BidderError.create("bidder1_error1"))));
        givenHttpConnector("bidder2", mock(BidderRequester.class), BidderSeatBid.of(
                singletonList(givenBid(Bid.builder().build())), emptyList(),
                asList(BidderError.create("bidder2_error1"), BidderError.create("bidder2_error2"))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(doubleMap("bidder1", 1, "bidder2", 2)));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout).result();

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
                singletonList(givenBid(Bid.builder().build())),
                singletonList(ExtHttpCall.builder()
                        .uri("bidder1_uri1")
                        .requestbody("bidder1_requestBody1")
                        .status(200)
                        .responsebody("bidder1_responseBody1")
                        .build()),
                emptyList()));
        givenHttpConnector("bidder2", mock(BidderRequester.class), BidderSeatBid.of(
                singletonList(givenBid(Bid.builder().build())),
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
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout).result();

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
        assertThat(ext.getDebug().getResolvedrequest()).isEqualTo(bidRequest);
    }

    @Test
    public void shouldNotPopulateBidResponseDebugExtensionIfTestFlagIsFalse() throws JsonProcessingException {
        // given
        givenHttpConnector(BidderSeatBid.of(
                singletonList(givenBid(Bid.builder().build())),
                singletonList(ExtHttpCall.builder()
                        .uri("bidder1_uri1")
                        .requestbody("bidder1_requestBody1")
                        .status(200)
                        .responsebody("bidder1_responseBody1")
                        .build()),
                emptyList()));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder1", 1)));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout).result();

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
        final Future<BidResponse> result = exchangeService.holdAuction(bidRequest, uidsCookie, timeout);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class)
                .hasMessageStartingWith("Error decoding bidRequest.ext: ");
    }

    @Test
    public void shouldTolerateNullRequestExtPrebid() {
        // given
        givenHttpConnector(givenSingleSeatBid(givenBid(Bid.builder().build())));

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.ext(mapper.valueToTree(singletonMap("someField", 1))));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(bid -> toExtPrebid(bid.getExt()).getPrebid().getTargeting())
                .allSatisfy(map -> assertThat(map).isNull());
    }

    @Test
    public void shouldTolerateNullRequestExtPrebidTargeting() {
        // given
        givenHttpConnector(givenSingleSeatBid(givenBid(Bid.builder().build())));

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.ext(mapper.valueToTree(singletonMap("prebid", singletonMap("someField", 1)))));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(bid -> toExtPrebid(bid.getExt()).getPrebid().getTargeting())
                .allSatisfy(map -> assertThat(map).isNull());
    }

    @Test
    public void shouldTolerateResponseBidValidationErrors() throws JsonProcessingException {
        // given
        givenHttpConnector("bidder1", mock(BidderRequester.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(1.23)).build()))));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                        null, null, ExtRequestTargeting.of(Json.mapper.valueToTree(singletonList(ExtPriceGranularityBucket.of(
                                2, BigDecimal.valueOf(0), BigDecimal.valueOf(5), BigDecimal.valueOf(0.5)))), true),
                        null, null)))));

        given(responseBidValidator.validate(any()))
                .willReturn(ValidationResult.error("bid validation error"));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout).result();

        // then
        final ExtBidResponse ext = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);
        assertThat(ext.getErrors()).hasSize(1).containsOnly(
                entry("bidder1", singletonList("bid validation error")));
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
                        null, null, ExtRequestTargeting.of(Json.mapper.valueToTree(singletonList(ExtPriceGranularityBucket.of(
                                2, BigDecimal.valueOf(0), BigDecimal.valueOf(5), BigDecimal.valueOf(0.5)))), null),
                        null, null)))));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout).result();

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
                        null, null, ExtRequestTargeting.of(Json.mapper.valueToTree(singletonList(ExtPriceGranularityBucket.of(
                                2, BigDecimal.valueOf(0), BigDecimal.valueOf(5), BigDecimal.valueOf(0.5)))), null), null,
                        ExtRequestPrebidCache.of(mapper.createObjectNode()))))));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout).result();

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
                        null, null, ExtRequestTargeting.of(Json.mapper.valueToTree(singletonList(ExtPriceGranularityBucket.of(
                                2, BigDecimal.valueOf(0), BigDecimal.valueOf(5), BigDecimal.valueOf(0.5)))), null), null,
                        ExtRequestPrebidCache.of(mapper.createObjectNode()))))));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout).result();

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
                        null, null, ExtRequestTargeting.of(Json.mapper.valueToTree(singletonList(ExtPriceGranularityBucket.of(
                                2, BigDecimal.valueOf(0), BigDecimal.valueOf(5), BigDecimal.valueOf(0.5)))), null), null,
                        ExtRequestPrebidCache.of(mapper.createObjectNode()))))));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout).result();

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
    public void shouldPopulateTargetingKeywordsForWinningBidsAndWinningBidsByBidder() {
        // given
        givenHttpConnector("bidder1", mock(BidderRequester.class), givenSeatBid(asList(
                givenBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(1.23)).build()),
                givenBid(Bid.builder().id("bidId2").impid("impId1").price(BigDecimal.valueOf(4.56)).build()))));
        givenHttpConnector("bidder2", mock(BidderRequester.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().id("bidId3").impid("impId1").price(BigDecimal.valueOf(7.89)).build()))));

        final BidRequest bidRequest = givenBidRequest(asList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1")),
                givenImp(doubleMap("bidder1", 1, "bidder2", 2), builder -> builder.id("impId1"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                        null, null, ExtRequestTargeting.of(Json.mapper.valueToTree(singletonList(ExtPriceGranularityBucket.of(
                                2, BigDecimal.valueOf(0), BigDecimal.valueOf(5), BigDecimal.valueOf(0.5)))), null), null,
                        null)))));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(
                        Bid::getId,
                        bid -> toTargetingByKey(bid, "hb_bidder"),
                        bid -> toTargetingByKey(bid, "hb_bidder_bidder1"))
                .containsOnly(
                        tuple("bidId1", null, null),
                        tuple("bidId2", null, "bidder1"), // winning bid for separate bidder
                        tuple("bidId3", "bidder2", null)); // winning bid through all bids
    }

    @Test
    public void shouldNotPopulateWinningBidTargetingIfIncludeWinnersFlagIsFalse() {
        // given
        givenHttpConnector("bidder1", mock(BidderRequester.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(1.23)).build()))));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                        null, null, ExtRequestTargeting.of(Json.mapper.valueToTree(singletonList(ExtPriceGranularityBucket.of(
                                2, BigDecimal.valueOf(0), BigDecimal.valueOf(5), BigDecimal.valueOf(0.5)))), false),
                        null, null)))));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(
                        Bid::getId,
                        bid -> toTargetingByKey(bid, "hb_bidder"),
                        bid -> toTargetingByKey(bid, "hb_bidder_bidder1"))
                .containsOnly(
                        tuple("bidId1", null, "bidder1"));
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
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout);

        // then
        final BidRequest capturedBidRequest = captureBidRequest();
        assertThat(capturedBidRequest.getUser()).isSameAs(user);
    }

    @Test
    public void shouldHonorBuyeridFromRequestAndClearBuyerIdsFromUserExtPrebidIfContains() {
        // given
        givenHttpConnector(givenEmptySeatBid());

        given(uidsCookie.uidFrom(anyString())).willReturn("buyeridFromCookie");
        final Map<String, String> uids = new HashMap<>();
        uids.put("someBidder", "uidval");

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.user(User.builder().buyeruid("buyeridFromRequest").ext(mapper.valueToTree(
                        ExtUser.of(ExtUserPrebid.of(uids), null, null))).build()));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout);

        // then
        final User capturedBidRequestUser = captureBidRequest().getUser();
        assertThat(capturedBidRequestUser).isEqualTo(User.builder().buyeruid("buyeridFromRequest")
                .ext(mapper.valueToTree(ExtUser.of(ExtUserPrebid.of(null), null, null))).build());

    }

    @Test
    public void shouldSetUserBuyerIdsFromUserExtPrebidAndClearPrebidBuyerIdsAfterwards() {
        // given
        givenHttpConnector(givenEmptySeatBid());
        final Map<String, String> uids = new HashMap<>();
        uids.put("someBidder", "uidval");

        given(uidsCookie.uidFrom(anyString())).willReturn("buyeridFromCookie");

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.user(User.builder().ext(mapper.valueToTree(
                        ExtUser.of(ExtUserPrebid.of(uids), null, null))).build()));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout);

        // then
        final User capturedBidRequestUser = captureBidRequest().getUser();
        assertThat(capturedBidRequestUser).isEqualTo(User.builder().buyeruid("uidval")
                .ext(mapper.valueToTree(ExtUser.of(ExtUserPrebid.of(null), null, null))).build());

    }

    @Test
    public void shouldAddBuyeridToUserFromRequest() {
        // given
        givenHttpConnector(givenEmptySeatBid());
        given(uidsCookie.uidFrom(eq("cookieFamily"))).willReturn("buyerid");

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.user(User.builder().id("userId").build()));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout);

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
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout);

        // then
        final BidRequest capturedBidRequest = captureBidRequest();
        assertThat(capturedBidRequest.getUser()).isEqualTo(User.builder().buyeruid("buyerid").build());
    }

    @Test
    public void shouldPassGlobalTimeoutToConnectorUnchangedIfCachingIsNotRequested() {
        // given
        givenHttpConnector(givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout);

        // then
        verify(bidderRequester).requestBids(any(), same(timeout));
    }

    @Test
    public void shouldPassReducedGlobalTimeoutToConnectorAndOriginalToCacheServiceIfCachingIsRequested() {
        // given
        exchangeService = new ExchangeService(bidderCatalog, responseBidValidator, cacheService, metrics, clock, 100);

        givenHttpConnector(givenSeatBid(singletonList(
                givenBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build()))));

        given(cacheService.cacheBidsOpenrtb(anyList(), any()))
                .willReturn(Future.succeededFuture(singletonList("cacheId1")));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                        null, null, ExtRequestTargeting.of(Json.mapper.valueToTree(singletonList(ExtPriceGranularityBucket.of(
                                2, BigDecimal.valueOf(0), BigDecimal.valueOf(5), BigDecimal.valueOf(0.5)))), null),
                        null, ExtRequestPrebidCache.of(mapper.createObjectNode()))))));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout).result();

        // then
        final ArgumentCaptor<Timeout> timeoutCaptor = ArgumentCaptor.forClass(Timeout.class);
        verify(bidderRequester).requestBids(any(), timeoutCaptor.capture());
        assertThat(timeoutCaptor.getValue().remaining()).isEqualTo(400L);
        verify(cacheService).cacheBidsOpenrtb(anyList(), same(timeout));
    }

    @Test
    public void shouldIncrementRequestAndNoCookieAndUpdatePriceAndRequestTimeMetrics() {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(true);

        given(bidderRequester.requestBids(any(), any()))
                .willReturn(Future.succeededFuture(givenSeatBid(singletonList(
                        givenBid(Bid.builder().price(TEN).build())))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("somebidder", 1)));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout);

        // then
        verify(adapterMetrics).incCounter(eq(MetricName.requests));
        verify(adapterMetrics).incCounter(eq(MetricName.no_cookie_requests));
        verify(adapterMetrics).updateTimer(eq(MetricName.request_time), anyLong());
        verify(adapterMetrics).updateHistogram(eq(MetricName.prices), anyLong());
    }

    @Test
    public void shouldUpdatePriceMetricWithZeroValueIfBidPriceNull() {
        // given
        given(bidderRequester.requestBids(any(), any()))
                .willReturn(Future.succeededFuture(givenSeatBid(singletonList(
                        givenBid(Bid.builder().price(null).build())))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("somebidder", 1)));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout);

        // then
        verify(adapterMetrics).updateHistogram(eq(MetricName.prices), eq(0L));
    }

    @Test
    public void shouldIncrementNoBidRequestsMetric() {
        // given
        given(bidderRequester.requestBids(any(), any()))
                .willReturn(Future.succeededFuture(givenSeatBid(emptyList())));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("somebidder", 1)));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout);

        // then
        verify(adapterMetrics).incCounter(eq(MetricName.no_bid_requests));
    }

    @Test
    public void shouldReturnBidsWithAdjustedPricesWhenAdjustmentFactorPresent() {
        // given
        final BidderRequester bidderRequester = mock(BidderRequester.class);
        givenHttpConnector("bidder", bidderRequester, givenSeatBid(singletonList(
                givenBid(Bid.builder().price(BigDecimal.valueOf(2)).build()))));

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(emptyMap(),
                        singletonMap("bidder", BigDecimal.valueOf(2.468)), null, null, null)))));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getPrice).containsExactly(BigDecimal.valueOf(4.936));
    }

    @Test
    public void shouldReturnBidsWithoutAdjustingPricesWhenAdjustmentFactorNotPresentForBidder() {
        // given
        final BidderRequester bidderRequester = mock(BidderRequester.class);
        givenHttpConnector("bidder", bidderRequester, givenSeatBid(singletonList(
                givenBid(Bid.builder().price(BigDecimal.valueOf(2.0)).build()))));

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(emptyMap(),
                        singletonMap("some-other-bidder", BigDecimal.TEN), null, null, null)))));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest, uidsCookie, timeout).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getPrice).containsExactly(BigDecimal.valueOf(2.0));
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
        given(bidderRequester.requestBids(any(), any())).willReturn(Future.succeededFuture(response));
    }

    private void givenHttpConnector(String bidderRequesterName, BidderRequester bidderRequester,
                                    BidderSeatBid response) {
        given(bidderCatalog.isValidName(eq(bidderRequesterName))).willReturn(true);
        given(bidderCatalog.bidderRequesterByName(eq(bidderRequesterName))).willReturn(bidderRequester);
        given(bidderRequester.requestBids(any(), any())).willReturn(Future.succeededFuture(response));
    }

    private static BidderSeatBid givenSeatBid(List<BidderBid> bids) {
        return BidderSeatBid.of(bids, emptyList(), emptyList());
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

    private static String toTargetingByKey(Bid bid, String targetingKey) {
        final Map<String, String> targeting = toExtPrebid(bid.getExt()).getPrebid().getTargeting();
        return targeting != null ? targeting.get(targetingKey) : null;
    }
}
