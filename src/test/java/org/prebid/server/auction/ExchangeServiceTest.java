package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BidRequest.BidRequestBuilder;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Imp.ImpBuilder;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import org.apache.commons.collections4.MapUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.auction.model.PrivacyEnforcementResult;
import org.prebid.server.auction.model.StoredResponseResult;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cache.model.CacheContext;
import org.prebid.server.cache.model.CacheIdInfo;
import org.prebid.server.cache.model.CacheServiceResult;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.events.EventsService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheBids;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserPrebid;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.Events;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidderError;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.proto.response.BidderInfo;
import org.prebid.server.validation.ResponseBidValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.math.BigDecimal.TEN;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.Function.identity;
import static org.apache.commons.lang3.exception.ExceptionUtils.rethrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;

public class ExchangeServiceTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private Usersyncer usersyncer;
    @Mock
    private StoredResponseProcessor storedResponseProcessor;
    @Mock
    private PrivacyEnforcementService privacyEnforcementService;
    @Mock
    private HttpBidderRequester httpBidderRequester;
    @Mock
    private ResponseBidValidator responseBidValidator;
    @Mock
    private CurrencyConversionService currencyService;
    @Mock
    private EventsService eventsService;
    @Mock
    private CacheService cacheService;
    @Mock
    private BidResponseCreator bidResponseCreator;
    @Spy
    private BidResponsePostProcessor.NoOpBidResponsePostProcessor bidResponsePostProcessor;
    @Mock
    private Metrics metrics;
    @Mock
    private UidsCookie uidsCookie;

    private Clock clock;

    private ExchangeService exchangeService;

    private Timeout timeout;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        given(bidResponseCreator.create(anyList(), any(), any(), any(), any(), anyMap(),
                anyBoolean())).willReturn(givenBidResponseWithBids(singletonList(givenBid(identity())), null));

        given(bidderCatalog.isValidName(anyString())).willReturn(true);
        given(bidderCatalog.isActive(anyString())).willReturn(true);
        given(bidderCatalog.usersyncerByName(anyString())).willReturn(usersyncer);

        given(privacyEnforcementService.mask(argThat(MapUtils::isNotEmpty), any(), any(), any(), any(), any(), any()))
                .willAnswer(inv ->
                        Future.succeededFuture(((Map<String, User>) inv.getArgument(0)).entrySet().stream()
                                .collect(HashMap::new, (map, bidderToUserEntry) -> map.put(bidderToUserEntry.getKey(),
                                        PrivacyEnforcementResult.of(bidderToUserEntry.getValue(), null, null)),
                                        HashMap::putAll)));

        given(privacyEnforcementService.mask(argThat(MapUtils::isEmpty), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(emptyMap()));

        given(bidderCatalog.bidderInfoByName(anyString())).willReturn(givenBidderInfo(15, true));

        given(responseBidValidator.validate(any())).willReturn(ValidationResult.success());
        given(usersyncer.getCookieFamilyName()).willReturn("cookieFamily");

        given(currencyService.convertCurrency(any(), any(), any(), any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(eventsService.isEventsEnabled(any(), any())).willReturn(Future.succeededFuture(false));
        given(storedResponseProcessor.getStoredResponseResult(any(), any(), any()))
                .willAnswer(inv -> Future.succeededFuture(StoredResponseResult.of(inv.getArgument(0), emptyList())));
        given(storedResponseProcessor.mergeWithBidderResponses(any(), any(), any())).willAnswer(inv -> inv.getArgument(0));

        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        timeout = new TimeoutFactory(clock).create(500);

        exchangeService = new ExchangeService(bidderCatalog, storedResponseProcessor, privacyEnforcementService,
                httpBidderRequester, responseBidValidator, currencyService, eventsService, cacheService,
                bidResponseCreator, bidResponsePostProcessor, metrics, clock, 0);
    }

    @Test
    public void creationShouldFailOnNegativeExpectedCacheTime() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ExchangeService(bidderCatalog, storedResponseProcessor, privacyEnforcementService,
                        httpBidderRequester, responseBidValidator, currencyService, eventsService, cacheService,
                        bidResponseCreator, bidResponsePostProcessor, metrics, clock, -1));
    }

    @Test
    public void shouldTolerateImpWithoutExtension() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(null));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        verifyZeroInteractions(bidderCatalog);
        verifyZeroInteractions(httpBidderRequester);
        assertThat(bidResponse).isNotNull();
    }

    @Test
    public void shouldTolerateImpWithUnknownBidderInExtension() {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(false);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("invalid", 0)));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        verify(bidderCatalog).isValidName(eq("invalid"));
        verifyZeroInteractions(httpBidderRequester);
        assertThat(bidResponse).isNotNull();
    }

    @Test
    public void shouldTolerateMissingPrebidImpExtension() {
        // given
        givenBidder(givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final BidRequest capturedBidRequest = captureBidRequest();
        assertThat(capturedBidRequest.getImp()).hasSize(1)
                .element(0).returns(mapper.valueToTree(ExtPrebid.of(null, 1)), Imp::getExt);
    }

    @Test
    public void shouldExtractRequestWithBidderSpecificExtension() {
        // given
        givenBidder(givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(singletonList(
                givenImp(doubleMap("prebid", 0, "someBidder", 1), builder -> builder
                        .id("impId")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(300).build()))
                                .build()))),
                builder -> builder.id("requestId").tmax(500L));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final BidRequest capturedBidRequest = captureBidRequest();
        assertThat(capturedBidRequest).isEqualTo(BidRequest.builder()
                .id("requestId")
                .cur(singletonList("USD"))
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
        final Bidder<?> bidder1 = mock(Bidder.class);
        final Bidder<?> bidder2 = mock(Bidder.class);
        givenBidder("bidder1", bidder1, givenEmptySeatBid());
        givenBidder("bidder2", bidder2, givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(asList(
                givenImp(doubleMap("bidder1", 1, "bidder2", 2), identity()),
                givenImp(singletonMap("bidder1", 3), identity())));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidRequest> bidRequest1Captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpBidderRequester).requestBids(same(bidder1), bidRequest1Captor.capture(), any(), anyBoolean());
        final BidRequest capturedBidRequest1 = bidRequest1Captor.getValue();
        assertThat(capturedBidRequest1.getImp()).hasSize(2)
                .extracting(imp -> imp.getExt().get("bidder").asInt())
                .containsOnly(1, 3);

        final ArgumentCaptor<BidRequest> bidRequest2Captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpBidderRequester).requestBids(same(bidder2), bidRequest2Captor.capture(), any(), anyBoolean());
        final BidRequest capturedBidRequest2 = bidRequest2Captor.getValue();
        assertThat(capturedBidRequest2.getImp()).hasSize(1)
                .element(0).returns(2, imp -> imp.getExt().get("bidder").asInt());
    }

    @Test
    public void shouldReturnFailedFutureWithNotChangedMessageWhenPrivacyEnforcementServiceRespondMaskWithFailedFuture() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());

        given(privacyEnforcementService.mask(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.failedFuture("Error when retrieving allowed purpose ids"));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .regs(Regs.of(null, mapper.valueToTree(ExtRegs.of(1)))));

        // when
        final Future<?> result = exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).hasMessage("Error when retrieving allowed purpose ids");
    }

    @Test
    public void shouldReturnFailedFutureWhenGPrebidExceptionIfExtRegsCannotBeParsed() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());

        given(privacyEnforcementService.mask(any(), any(), any(), any(), any(), any(), any()))
                .willThrow(new PreBidException("Error decoding bidRequest.regs.ext:invalid"));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .regs(Regs.of(null, mapper.createObjectNode().put("gdpr", "invalid"))));

        // when
        final Future<?> result = exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class)
                .hasMessageStartingWith("Error decoding bidRequest.regs.ext:invalid");
    }

    @Test
    public void shouldExtractRequestByAliasForCorrectBidder() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(singletonList(
                givenImp(singletonMap("bidderAlias", 1), identity())),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("bidderAlias", "bidder")).build()))));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpBidderRequester).requestBids(same(bidder), bidRequestCaptor.capture(), any(), anyBoolean());
        assertThat(bidRequestCaptor.getValue().getImp()).hasSize(1)
                .extracting(imp -> imp.getExt().get("bidder").asInt())
                .contains(1);
    }

    @Test
    public void shouldExtractMultipleRequestsForTheSameBidderIfAliasesWereUsed() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(singletonList(
                givenImp(doubleMap("bidder", 1, "bidderAlias", 2), identity())),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("bidderAlias", "bidder")).build()))));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpBidderRequester, times(2)).requestBids(same(bidder), bidRequestCaptor.capture(), any(),
                anyBoolean());
        final List<BidRequest> capturedBidRequests = bidRequestCaptor.getAllValues();

        assertThat(capturedBidRequests).hasSize(2)
                .extracting(capturedBidRequest -> capturedBidRequest.getImp().get(0).getExt().get("bidder").asInt())
                .containsOnly(2, 1);
    }

    @Test
    public void shouldTolerateBidderResultWithoutBids() {
        // given
        givenBidder(givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        givenBidResponseCreator(emptyMap());

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(bidResponse.getSeatbid()).isEmpty();
    }

    @Test
    public void shouldReturnSeparateSeatBidsForTheSameBidderIfBiddersAliasAndBidderWereUsedWithingSingleImp() {
        // given
        given(bidderCatalog.isValidName("bidder")).willReturn(true);

        given(httpBidderRequester.requestBids(any(), eq(givenBidRequest(givenSingleImp(singletonMap("bidder", 1)),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("bidderAlias", "bidder")).build()))))), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(singletonList(
                        givenBid(Bid.builder().price(BigDecimal.ONE).build())))));

        given(httpBidderRequester.requestBids(any(), eq(givenBidRequest(givenSingleImp(singletonMap("bidder", 2)),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("bidderAlias", "bidder")).build()))))), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(singletonList(
                        givenBid(Bid.builder().price(BigDecimal.ONE).build())))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(doubleMap("bidder", 1, "bidderAlias", 2)),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("bidderAlias", "bidder")).build()))));

        given(bidResponseCreator.create(anyList(), any(), any(), any(), any(), anyMap(),
                anyBoolean()))
                .willReturn(BidResponse.builder()
                        .seatbid(asList(
                                givenSeatBid(singletonList(givenBid(identity())), identity()),
                                givenSeatBid(singletonList(givenBid(identity())), identity())))
                        .build());

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        verify(httpBidderRequester, times(2)).requestBids(any(), any(), any(), anyBoolean());
        assertThat(bidResponse.getSeatbid()).hasSize(2)
                .extracting(seatBid -> seatBid.getBid().size())
                .containsOnly(1, 1);
    }

    @Test
    public void shouldPopulateTargetingKeywordsForWinningBidsAndWinningBidsByBidder() {
        // given
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(asList(
                givenBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build()),
                givenBid(Bid.builder().id("bidId2").impid("impId1").price(BigDecimal.valueOf(4.98)).build()))));
        givenBidder("bidder2", mock(Bidder.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().id("bidId3").impid("impId1").price(BigDecimal.valueOf(7.25)).build()))));

        final BidRequest bidRequest = givenBidRequest(asList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1")),
                givenImp(doubleMap("bidder1", 1, "bidder2", 2), builder -> builder.id("impId1"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(givenTargeting())
                        .build()))));

        givenBidResponseCreator(asList(
                givenBid(bidBuilder -> bidBuilder
                        .id("bidId1")
                        .ext(mapper.valueToTree(ExtPrebid.of(ExtBidPrebid.of(null, null, null, null), null)))),
                givenBid(bidBuilder -> bidBuilder
                        .id("bidId2")
                        .ext(mapper.valueToTree(ExtPrebid.of(ExtBidPrebid.of(null,
                                singletonMap("hb_bidder_bidder1", "bidder1"), null, null), null)))),
                givenBid(bidBuilder -> bidBuilder
                        .id("bidId3")
                        .ext(mapper.valueToTree(ExtPrebid.of(ExtBidPrebid.of(null,
                                singletonMap("hb_bidder", "bidder2"), null, null), null))))
        ));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid).hasSize(3)
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
    @SuppressWarnings("unchecked")
    public void shouldCallBidResponseCreatorWithExpectedParams() {
        // given
        givenBidder("bidder1", mock(Bidder.class), givenEmptySeatBid());

        final Bid thirdBid = Bid.builder().id("bidId3").impid("impId1").price(BigDecimal.valueOf(7.89)).build();
        givenBidder("bidder2", mock(Bidder.class), givenSeatBid(singletonList(givenBid(thirdBid))));

        final BidRequest bidRequest = givenBidRequest(asList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1")),
                givenImp(doubleMap("bidder1", 1, "bidder2", 2), builder -> builder.id("impId1"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(givenTargeting())
                        .build()))));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        final ArgumentCaptor<List<BidderResponse>> captor = ArgumentCaptor.forClass(List.class);
        verify(bidResponseCreator).create(
                captor.capture(),
                eq(bidRequest),
                eq(givenTargeting()),
                any(), any(), anyMap(), eq(false));

        assertThat(captor.getValue()).containsOnly(
                BidderResponse.of("bidder2", BidderSeatBid.of(singletonList(
                        BidderBid.of(thirdBid, banner, null)), emptyList(), emptyList()), 0),
                BidderResponse.of("bidder1", BidderSeatBid.of(emptyList(), emptyList(), emptyList()), 0));
    }

    @Test
    public void shouldCallBidResponseCreatorWithEnabledDebugTrueIfTestFlagIsTrue() {
        // given
        givenBidder("bidder1", mock(Bidder.class), BidderSeatBid.of(
                singletonList(givenBid(Bid.builder().price(BigDecimal.ONE).build())),
                singletonList(ExtHttpCall.builder()
                        .uri("bidder1_uri1")
                        .requestbody("bidder1_requestBody1")
                        .status(200)
                        .responsebody("bidder1_responseBody1")
                        .build()),
                emptyList()));

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap("bidder1", 1)),
                builder -> builder.test(1));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        verify(bidResponseCreator)
                .create(anyList(), eq(bidRequest), any(), any(), any(), anyMap(), eq(true));
    }

    @Test
    public void shouldCallBidResponseCreatorWithEnabledDebugTrueIfExtPrebidDebugIsOn() {
        // given
        givenBidder("bidder1", mock(Bidder.class), BidderSeatBid.of(
                singletonList(givenBid(Bid.builder().price(BigDecimal.ONE).build())),
                singletonList(ExtHttpCall.builder()
                        .uri("bidder1_uri1")
                        .requestbody("bidder1_requestBody1")
                        .status(200)
                        .responsebody("bidder1_responseBody1")
                        .build()),
                emptyList()));

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap("bidder1", 1)),
                builder -> builder.ext(
                        mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder().debug(1).build()))));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        verify(bidResponseCreator)
                .create(anyList(), eq(bidRequest), any(), any(), any(), anyMap(), eq(true));
    }

    @Test
    public void shouldReturnErrorIfRequestExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(emptyList(),
                builder -> builder.ext(mapper.valueToTree(singletonMap("prebid", 1))));

        // when
        final Future<BidResponse> result = exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class)
                .hasMessageStartingWith("Error decoding bidRequest.ext: ");
    }

    @Test
    public void shouldTolerateNullRequestExtPrebid() {
        // given
        givenBidder(givenSingleSeatBid(givenBid(Bid.builder().price(BigDecimal.ONE).build())));

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.ext(mapper.valueToTree(singletonMap("someField", 1))));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(bid -> toExtPrebid(bid.getExt()).getPrebid().getTargeting())
                .allSatisfy(map -> assertThat(map).isNull());
    }

    @Test
    public void shouldTolerateNullRequestExtPrebidTargeting() {
        // given
        givenBidder(givenSingleSeatBid(givenBid(Bid.builder().price(BigDecimal.ONE).build())));

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.ext(mapper.valueToTree(singletonMap("prebid", singletonMap("someField", 1)))));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(bid -> toExtPrebid(bid.getExt()).getPrebid().getTargeting())
                .allSatisfy(map -> assertThat(map).isNull());
    }

    @Test
    public void shouldTolerateResponseBidValidationErrors() throws JsonProcessingException {
        // given
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(1.23)).build()))));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .build()))));

        given(responseBidValidator.validate(any()))
                .willReturn(ValidationResult.error("bid validation error"));

        final List<ExtBidderError> bidderErrors = singletonList(ExtBidderError.of(BidderError.Type.generic.getCode(),
                "bid validation error"));
        givenBidResponseCreator(singletonMap("bidder1", bidderErrors));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        final ExtBidResponse ext = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);
        assertThat(ext.getErrors()).hasSize(1).containsOnly(
                entry("bidder1", bidderErrors));
    }

    @Test
    public void shouldCreateRequestsFromImpsReturnedByStoredResponseProcessor() {
        // given
        givenBidder(givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(asList(
                givenImp(doubleMap("prebid", 0, "someBidder1", 1), builder -> builder
                        .id("impId1")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(300).build()))
                                .build())),
                givenImp(doubleMap("prebid", 0, "someBidder2", 1), builder -> builder
                        .id("impId2")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(300).build()))
                                .build()))
                ),
                builder -> builder.id("requestId").tmax(500L));

        given(storedResponseProcessor.getStoredResponseResult(any(), any(), any()))
                .willReturn(Future.succeededFuture(StoredResponseResult
                        .of(singletonList(givenImp(doubleMap("prebid", 0, "someBidder1", 1), builder -> builder
                                .id("impId1")
                                .banner(Banner.builder()
                                        .format(singletonList(Format.builder().w(400).h(300).build()))
                                        .build()))), emptyList())));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        final BidRequest capturedBidRequest = captureBidRequest();
        assertThat(capturedBidRequest).isEqualTo(BidRequest.builder()
                .id("requestId")
                .cur(singletonList("USD"))
                .imp(singletonList(Imp.builder()
                        .id("impId1")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(300).build()))
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(0, 1)))
                        .build()))
                .tmax(500L)
                .build());
    }

    @Test
    public void shouldProcessBidderResponseReturnedFromStoredResponseProcessor() {
        // given
        givenBidder(givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(singletonList(
                givenImp(doubleMap("prebid", 0, "someBidder", 1), builder -> builder
                        .id("impId")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(300).build()))
                                .build()))),
                builder -> builder.id("requestId").tmax(500L));

        given(storedResponseProcessor.mergeWithBidderResponses(any(), any(), any()))
                .willReturn(singletonList(BidderResponse.of("someBidder",
                        BidderSeatBid.of(singletonList(BidderBid.of(Bid.builder().id("bidId1").build(),
                                BidType.banner, "USD")), null, emptyList()), 100)));

        givenBidResponseCreator(singletonList(Bid.builder().id("bidId1").build()));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(Bid::getId)
                .containsOnly("bidId1");
    }

    @Test
    public void shouldReturnFailedFutureWhenStoredResponseProcessorGetStoredResultReturnsFailedFuture() {
        // given
        given(storedResponseProcessor.getStoredResponseResult(any(), any(), any()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Error")));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                givenImp(doubleMap("prebid", 0, "someBidder", 1), builder -> builder
                        .id("impId")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(300).build()))
                                .build()))),
                builder -> builder.id("requestId").tmax(500L));


        // when
        final Future<BidResponse> result = exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(InvalidRequestException.class).hasMessage("Error");
    }

    @Test
    public void shouldReturnFailedFutureWhenStoredResponseProcessorMergeBidderResponseReturnsFailedFuture() {
        // given
        givenBidder(givenEmptySeatBid());

        given(storedResponseProcessor.mergeWithBidderResponses(any(), any(), any()))
                .willThrow(new PreBidException("Error"));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                givenImp(doubleMap("prebid", 0, "someBidder", 1), builder -> builder
                        .id("impId")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(300).build()))
                                .build()))),
                builder -> builder.id("requestId").tmax(500L));

        // when
        final Future<BidResponse> result = exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class).hasMessage("Error");
    }

    @Test
    public void shouldNotModifyUserFromRequestIfNoBuyeridInCookie() {
        // given
        givenBidder(givenEmptySeatBid());

        // this is not required but stated for clarity's sake
        given(uidsCookie.uidFrom(anyString())).willReturn(null);

        final User user = User.builder().id("userId").build();
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.user(user));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final BidRequest capturedBidRequest = captureBidRequest();
        assertThat(capturedBidRequest.getUser()).isSameAs(user);
    }

    @Test
    public void shouldHonorBuyeridFromRequestAndClearBuyerIdsFromUserExtPrebidIfContains() {
        // given
        givenBidder(givenEmptySeatBid());

        given(uidsCookie.uidFrom(anyString())).willReturn("buyeridFromCookie");

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.user(User.builder()
                        .buyeruid("buyeridFromRequest")
                        .ext(mapper.valueToTree(ExtUser.builder()
                                .prebid(ExtUserPrebid.of(singletonMap("someBidder", "uidval")))
                                .build()))
                        .build()));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final User capturedBidRequestUser = captureBidRequest().getUser();
        assertThat(capturedBidRequestUser).isEqualTo(User.builder()
                .buyeruid("buyeridFromRequest")
                .ext(mapper.valueToTree(ExtUser.builder().build()))
                .build());
    }

    @Test
    public void shouldSetUserBuyerIdsFromUserExtPrebidAndClearPrebidBuyerIdsAfterwards() {
        // given
        givenBidder(givenEmptySeatBid());

        given(uidsCookie.uidFrom(anyString())).willReturn("buyeridFromCookie");

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.user(User.builder()
                        .ext(mapper.valueToTree(ExtUser.builder()
                                .prebid(ExtUserPrebid.of(singletonMap("someBidder", "uidval")))
                                .build()))
                        .build()));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final User capturedBidRequestUser = captureBidRequest().getUser();
        assertThat(capturedBidRequestUser).isEqualTo(User.builder()
                .buyeruid("uidval")
                .ext(mapper.valueToTree(ExtUser.builder().build()))
                .build());
    }

    @Test
    public void shouldCleanRequestExtPrebidDataBidders() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(asList("someBidder", "should_be_removed")))
                        .aliases(singletonMap("someBidder", "alias_should_stay"))
                        .build()))));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final ObjectNode capturedBidRequestExt = captureBidRequest().getExt();
        assertThat(capturedBidRequestExt).isEqualTo(mapper.valueToTree(
                ExtBidRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("someBidder", "alias_should_stay"))
                        .data(ExtRequestPrebidData.of(singletonList("someBidder")))
                        .build())));
    }

    @Test
    public void shouldPassUserExtDataOnlyForAllowedBidder() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());
        givenBidder("missingBidder", bidder, givenEmptySeatBid());

        final ObjectNode dataNode = mapper.createObjectNode().put("data", "value");
        final Map<String, Integer> bidderToGdpr = doubleMap("someBidder", 1, "missingBidder", 0);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(bidderToGdpr),
                builder -> builder
                        .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                                .data(ExtRequestPrebidData.of(singletonList("someBidder"))).build())))
                        .user(User.builder()
                                .ext(mapper.valueToTree(ExtUser.builder().data(dataNode).build()))
                                .build()));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpBidderRequester, times(2)).requestBids(any(), bidRequestCaptor.capture(), any(), anyBoolean());
        final List<BidRequest> capturedBidRequests = bidRequestCaptor.getAllValues();

        assertThat(capturedBidRequests)
                .extracting(BidRequest::getUser)
                .containsOnly(
                        User.builder().ext(mapper.valueToTree(ExtUser.builder().data(dataNode).build())).build(),
                        User.builder().ext(mapper.createObjectNode()).build());
    }

    @Test
    public void shouldPassSiteExtDataOnlyForAllowedBidder() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());
        givenBidder("missingBidder", bidder, givenEmptySeatBid());

        final ObjectNode dataNode = mapper.createObjectNode().put("data", "value");
        final Map<String, Integer> bidderToGdpr = doubleMap("someBidder", 1, "missingBidder", 0);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(bidderToGdpr),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(singletonList("someBidder"))).build())))
                        .site(Site.builder().ext(mapper.valueToTree(ExtSite.of(0, dataNode))).build()));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpBidderRequester, times(2)).requestBids(any(), bidRequestCaptor.capture(), any(), anyBoolean());
        final List<BidRequest> capturedBidRequests = bidRequestCaptor.getAllValues();

        assertThat(capturedBidRequests)
                .extracting(BidRequest::getSite)
                .containsOnly(
                        Site.builder().ext(mapper.valueToTree(ExtSite.of(0, dataNode))).build(),
                        Site.builder().ext(mapper.valueToTree(ExtSite.of(0, null))).build());
    }

    @Test
    public void shouldPassAppExtDataOnlyForAllowedBidder() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());
        givenBidder("missingBidder", bidder, givenEmptySeatBid());

        final ObjectNode dataNode = mapper.createObjectNode().put("data", "value");
        final Map<String, Integer> bidderToGdpr = doubleMap("someBidder", 1, "missingBidder", 0);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(bidderToGdpr),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(singletonList("someBidder"))).build())))
                        .app(App.builder().ext(mapper.valueToTree(ExtApp.of(null, dataNode))).build()));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpBidderRequester, times(2)).requestBids(any(), bidRequestCaptor.capture(), any(), anyBoolean());
        final List<BidRequest> capturedBidRequests = bidRequestCaptor.getAllValues();

        assertThat(capturedBidRequests)
                .extracting(BidRequest::getApp)
                .containsOnly(
                        App.builder().ext(mapper.valueToTree(ExtApp.of(null, dataNode))).build(),
                        App.builder().ext(mapper.createObjectNode()).build());
    }

    @Test
    public void shouldAddBuyeridToUserFromRequest() {
        // given
        givenBidder(givenEmptySeatBid());
        given(uidsCookie.uidFrom(eq("cookieFamily"))).willReturn("buyerid");

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.user(User.builder().id("userId").build()));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final User capturedUser = captureBidRequest().getUser();
        assertThat(capturedUser).isEqualTo(User.builder().id("userId").buyeruid("buyerid").build());
    }

    @Test
    public void shouldCreateUserIfMissingInRequestAndBuyeridPresentInCookie() {
        // given
        givenBidder(givenEmptySeatBid());

        given(uidsCookie.uidFrom(eq("cookieFamily"))).willReturn("buyerid");

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final User capturedUser = captureBidRequest().getUser();
        assertThat(capturedUser).isEqualTo(User.builder().buyeruid("buyerid").build());
    }

    @Test
    public void shouldPassGlobalTimeoutToConnectorUnchangedIfCachingIsNotRequested() {
        // given
        givenBidder(givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        verify(httpBidderRequester).requestBids(any(), any(), same(timeout), anyBoolean());
    }

    @Test
    public void shouldPassReducedGlobalTimeoutToConnectorAndOriginalToCacheServiceIfCachingIsRequested() {
        // given
        exchangeService = new ExchangeService(bidderCatalog, storedResponseProcessor, privacyEnforcementService,
                httpBidderRequester, responseBidValidator, currencyService, eventsService, cacheService,
                bidResponseCreator, bidResponsePostProcessor, metrics, clock, 100);

        final Bid bid = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        givenBidder(givenSeatBid(singletonList(givenBid(bid))));

        given(cacheService.cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any()))
                .willReturn(Future.succeededFuture(givenCacheServiceResult(bid, null, null)));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(givenTargeting())
                        .cache(ExtRequestPrebidCache.of(ExtRequestPrebidCacheBids.of(null, null), null))
                        .build()))));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        final ArgumentCaptor<Timeout> timeoutCaptor = ArgumentCaptor.forClass(Timeout.class);
        verify(httpBidderRequester).requestBids(any(), any(), timeoutCaptor.capture(), anyBoolean());
        assertThat(timeoutCaptor.getValue().remaining()).isEqualTo(400L);
        verify(cacheService).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), same(timeout));
    }

    @Test
    public void shouldRequestCacheServiceWithExpectedArguments() {
        // given
        final Bid bid1 = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        final Bid bid2 = Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.valueOf(7.19)).build();
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(singletonList(givenBid(bid1))));
        givenBidder("bidder2", mock(Bidder.class), givenSeatBid(singletonList(givenBid(bid2))));

        // imp ids are not really used for matching, included them here for clarity
        final Imp imp1 = givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1"));
        final Imp imp2 = givenImp(singletonMap("bidder2", 2), builder -> builder.id("impId2"));
        final BidRequest bidRequest = givenBidRequest(asList(imp1, imp2),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(givenTargeting())
                        .cache(ExtRequestPrebidCache.of(ExtRequestPrebidCacheBids.of(null, null), null))
                        .build()))));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        verify(cacheService).cacheBidsOpenrtb(
                argThat(t -> t.containsAll(asList(bid1, bid2))), eq(asList(imp1, imp2)),
                eq(CacheContext.of(true, null, false, null)),
                eq(""), eq(timeout));
    }

    @Test
    public void shouldCallCacheServiceEvenRoundedCpmIsZero() {
        // given
        final Bid bid1 = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(0.05)).build();
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(singletonList(givenBid(bid1))));

        // imp ids are not really used for matching, included them here for clarity
        final Imp imp1 = givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1"));
        final BidRequest bidRequest = givenBidRequest(singletonList(imp1),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(givenTargeting())
                        .cache(ExtRequestPrebidCache.of(ExtRequestPrebidCacheBids.of(null, null), null))
                        .build()))));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        verify(cacheService).cacheBidsOpenrtb(argThat(bids -> bids.contains(bid1)), eq(singletonList(imp1)),
                eq(CacheContext.of(true, null, false, null)), eq(""), eq(timeout));
    }

    @Test
    public void shouldReturnBidsWithUpdatedPriceCurrencyConversion() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenSeatBid(singletonList(
                givenBid(Bid.builder().price(BigDecimal.valueOf(2.0)).build()))));

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                identity());

        final BigDecimal updatedPrice = BigDecimal.valueOf(5.0);
        given(currencyService.convertCurrency(any(), any(), any(), any())).willReturn(updatedPrice);

        givenBidResponseCreator(singletonList(Bid.builder().price(updatedPrice).build()));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getPrice).containsExactly(updatedPrice);
    }

    @Test
    public void shouldReturnSameBidPriceIfNoChangesAppliedToBidPrice() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenSeatBid(singletonList(
                givenBid(Bid.builder().price(BigDecimal.ONE).build()))));

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                identity());

        // returns the same price as in argument
        given(currencyService.convertCurrency(any(), any(), any(), any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getPrice).containsExactly(BigDecimal.ONE);
    }

    @Test
    public void shouldDropBidIfPrebidExceptionWasThrownDuringCurrencyConversion() throws JsonProcessingException {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenSeatBid(singletonList(
                givenBid(Bid.builder().price(BigDecimal.valueOf(2.0)).build()))));

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                identity());

        given(currencyService.convertCurrency(any(), any(), any(), any()))
                .willThrow(new PreBidException("no currency conversion available"));

        final List<ExtBidderError> bidderErrors = singletonList(ExtBidderError.of(BidderError.Type.generic.getCode(),
                "no currency conversion available"));
        givenBidResponseCreator(emptyList(), ExtBidResponse.of(null, singletonMap("bidder", bidderErrors),
                null, null, null));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid).isEmpty();
        final ExtBidResponse ext = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);
        assertThat(ext.getErrors()).hasSize(1)
                .containsOnly(entry("bidder", bidderErrors));
    }

    @Test
    public void shouldUpdateBidPriceWithCurrencyConversionAndPriceAdjustmentFactor() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenSeatBid(singletonList(
                givenBid(Bid.builder().price(BigDecimal.valueOf(2.0)).build()))));

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .aliases(emptyMap())
                        .bidadjustmentfactors(singletonMap("bidder", BigDecimal.valueOf(10.0)))
                        .build()))));

        given(currencyService.convertCurrency(any(), any(), any(), any())).willReturn(BigDecimal.valueOf(10.0));

        final BigDecimal updatedPrice = BigDecimal.valueOf(100);
        givenBidResponseCreator(singletonList(Bid.builder().price(updatedPrice).build()));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getPrice).containsExactly(updatedPrice);
    }

    @Test
    public void shouldUpdatePriceForOneBidAndDropAnotherIfPrebidExceptionHappensForSecondBid()
            throws JsonProcessingException {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenSeatBid(asList(
                givenBid(Bid.builder().price(BigDecimal.valueOf(2.0)).build()),
                givenBid(Bid.builder().price(BigDecimal.valueOf(3.0)).build()))));

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                identity());

        final BigDecimal updatedPrice = BigDecimal.valueOf(10.0);
        given(currencyService.convertCurrency(any(), any(), any(), any())).willReturn(updatedPrice)
                .willThrow(new PreBidException("no currency conversion available"));

        final List<ExtBidderError> bidderErrors = singletonList(ExtBidderError.of(BidderError.Type.generic.getCode(),
                "no currency conversion available"));
        givenBidResponseCreator(singletonList(Bid.builder().price(updatedPrice).build()), ExtBidResponse.of(
                null, singletonMap("bidder", bidderErrors), null, null, null));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getPrice).containsExactly(updatedPrice);
        final ExtBidResponse ext = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);
        assertThat(ext.getErrors()).hasSize(1).containsOnly(
                entry("bidder", bidderErrors));
    }

    @Test
    public void shouldRespondWithErrorWhenBidsWithUnsupportedCurrency() throws JsonProcessingException {
        // given
        final Bidder<?> bidderRequester = mock(Bidder.class);
        givenBidder("bidder", bidderRequester, givenSeatBid(singletonList(
                givenBid(Bid.builder().price(BigDecimal.valueOf(2.0)).build()))));

        final BidRequest bidRequest = BidRequest.builder().cur(Collections.singletonList("EUR"))
                .imp(singletonList(givenImp(singletonMap("bidder", 2), identity()))).build();

        // returns the same price as in argument
        given(currencyService.convertCurrency(any(), any(), any(), any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final List<ExtBidderError> bidderErrors = singletonList(ExtBidderError.of(BidderError.Type.generic.getCode(),
                "Bid currency is not allowed. Was EUR, wants: [USD]"));
        givenBidResponseCreator(singletonMap("bidder", bidderErrors));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(bidResponse.getSeatbid()).isEmpty();
        final ExtBidResponse ext = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);
        assertThat(ext.getErrors()).hasSize(1).containsOnly(
                entry("bidder", bidderErrors));
    }

    @Test
    public void shouldRespondWithErrorWhenBidsWithDifferentCurrencies() throws JsonProcessingException {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(true);

        given(httpBidderRequester.requestBids(any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(asList(
                        BidderBid.of(Bid.builder().price(TEN).build(), BidType.banner, "EUR"),
                        BidderBid.of(Bid.builder().price(TEN).build(), BidType.banner, "USD")))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.site(Site.builder().build()));

        final List<ExtBidderError> bidderErrors = singletonList(ExtBidderError.of(BidderError.Type.generic.getCode(),
                "Bid currencies mismatch found. Expected all bids to have the same currencies."));
        givenBidResponseCreator(singletonMap("someBidder", bidderErrors));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(bidResponse.getSeatbid()).isEmpty();
        final ExtBidResponse ext = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);
        assertThat(ext.getErrors()).hasSize(1)
                .containsOnly(entry("someBidder", bidderErrors));
    }

    @Test
    public void shouldAddExtPrebidEventsFromSitePublisher() {
        // given
        final Events events = Events.of("http://external.org/event?type=win&bidid=bidId&bidder=someBidder",
                "http://external.org/event?type=view&bidid=bidId&bidder=someBidder");
        given(eventsService.isEventsEnabled(anyString(), any())).willReturn(Future.succeededFuture(true));
        given(eventsService.createEvent(anyString(), anyString())).willReturn(events);

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.ONE)
                .ext(mapper.valueToTree(singletonMap("bidExt", 1))).build();
        givenBidder(BidderSeatBid.of(
                singletonList(BidderBid.of(bid, banner, null)),
                emptyList(),
                emptyList()));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder.site(Site.builder()
                        .publisher(Publisher.builder().id("1001").build()).build()));

        givenBidResponseCreator(singletonList(givenBid(
                bidBuilder -> bidBuilder.ext(mapper.valueToTree(ExtPrebid.of(
                        ExtBidPrebid.of(null, null, null, events), null))))));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        verify(eventsService).isEventsEnabled(eq("1001"), any());

        final Map<Bid, Events> expectedEventsByBids = singletonMap(bid,
                events);

        verify(bidResponseCreator).create(anyList(), eq(bidRequest), isNull(), any(), any(),
                eq(expectedEventsByBids), eq(false));

        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(responseBid -> toExtPrebid(responseBid.getExt()).getPrebid().getEvents())
                .containsOnly(events);
    }

    @Test
    public void shouldAddExtPrebidEventsFromAppPublisher() {
        // given
        final Events events = Events.of(
                "http://external.org/event?type=win&bidid=bidId&bidder=someBidder",
                "http://external.org/event?type=view&bidid=bidId&bidder=someBidder");
        given(eventsService.isEventsEnabled(anyString(), any())).willReturn(Future.succeededFuture(true));
        given(eventsService.createEvent(anyString(), anyString())).willReturn(events);

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.ONE).build();
        givenBidder(BidderSeatBid.of(
                singletonList(BidderBid.of(bid, banner, null)),
                emptyList(),
                emptyList()));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder.app(App.builder()
                        .publisher(Publisher.builder().id("1001").build()).build()));

        givenBidResponseCreator(singletonList(givenBid(
                bidBuilder -> bidBuilder.ext(mapper.valueToTree(ExtPrebid.of(
                        ExtBidPrebid.of(null, null, null, events), null))))));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        verify(eventsService).isEventsEnabled(eq("1001"), any());

        verify(bidResponseCreator).create(anyList(), eq(bidRequest), isNull(), any(), any(),
                eq(singletonMap(bid, events)), eq(false));

        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(responseBid -> toExtPrebid(responseBid.getExt()).getPrebid().getEvents())
                .containsOnly(events);
    }

    @Test
    public void shouldNotAddExtPrebidEventsWhenEventsServiceReturnsEmptyEventsService() {
        // given
        final BigDecimal price = BigDecimal.valueOf(2.0);
        givenBidder(BidderSeatBid.of(
                singletonList(BidderBid.of(
                        Bid.builder().id("bidId").price(price)
                                .ext(mapper.valueToTree(singletonMap("bidExt", 1))).build(), banner, null)),
                emptyList(),
                emptyList()));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder.app(App.builder()
                        .publisher(Publisher.builder().id("1001").build()).build()));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(bid -> toExtPrebid(bid.getExt()).getPrebid().getEvents())
                .containsNull();
    }

    @Test
    public void shouldIncrementCommonMetrics() {
        // given
        given(httpBidderRequester.requestBids(any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(singletonList(
                        givenBid(Bid.builder().price(TEN).build())))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.site(Site.builder().publisher(Publisher.builder().id("accountId").build()).build()));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        verify(metrics).updateAccountRequestMetrics(eq("accountId"), eq(MetricName.openrtb2web));
        verify(metrics)
                .updateAdapterRequestTypeAndNoCookieMetrics(eq("someBidder"), eq(MetricName.openrtb2web), eq(true));
        verify(metrics).updateAdapterResponseTime(eq("someBidder"), eq("accountId"), anyInt());
        verify(metrics).updateAdapterRequestGotbidsMetrics(eq("someBidder"), eq("accountId"));
        verify(metrics).updateAdapterBidMetrics(eq("someBidder"), eq("accountId"), eq(10000L), eq(false), eq("banner"));
    }

    @Test
    public void shouldCallUpdateCookieMetricsWithExpectedValue() {
        // given
        given(bidderCatalog.isActive(any())).willReturn(false);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.app(App.builder().build()));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        verify(metrics).updateAdapterRequestTypeAndNoCookieMetrics(
                eq("someBidder"), eq(MetricName.openrtb2web), eq(false));
    }

    @Test
    public void shouldUseEmptyStringIfPublisherIdIsNull() {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(true);
        given(httpBidderRequester.requestBids(any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(singletonList(
                        givenBid(Bid.builder().price(TEN).build())))));
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.site(Site.builder().publisher(Publisher.builder().build()).build()));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        verify(metrics).updateAccountRequestMetrics(eq(""), eq(MetricName.openrtb2web));
    }

    @Test
    public void shouldIncrementNoBidRequestsMetric() {
        // given
        given(httpBidderRequester.requestBids(any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(emptyList())));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.app(App.builder().publisher(Publisher.builder().id("accountId").build()).build()));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        verify(metrics).updateAdapterRequestNobidMetrics(eq("someBidder"), eq("accountId"));
    }

    @Test
    public void shouldIncrementGotBidsAndErrorMetricsIfBidderReturnsBidAndDifferentErrors() {
        // given
        given(httpBidderRequester.requestBids(any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(BidderSeatBid.of(
                        singletonList(givenBid(Bid.builder().price(TEN).build())),
                        emptyList(),
                        asList(
                                // two identical errors to verify corresponding metric is submitted only once
                                BidderError.badInput("rubicon error"),
                                BidderError.badInput("rubicon error"),
                                BidderError.badServerResponse("rubicon error"),
                                BidderError.failedToRequestBids("rubicon failed to request bids"),
                                BidderError.timeout("timeout error"),
                                BidderError.generic("timeout error")))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.site(Site.builder().publisher(Publisher.builder().id("accountId").build()).build()));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        verify(metrics).updateAdapterRequestGotbidsMetrics(eq("someBidder"), eq("accountId"));
        verify(metrics).updateAdapterRequestErrorMetric(eq("someBidder"), eq(MetricName.badinput));
        verify(metrics).updateAdapterRequestErrorMetric(eq("someBidder"), eq(MetricName.badserverresponse));
        verify(metrics).updateAdapterRequestErrorMetric(eq("someBidder"), eq(MetricName.failedtorequestbids));
        verify(metrics).updateAdapterRequestErrorMetric(eq("someBidder"), eq(MetricName.timeout));
        verify(metrics).updateAdapterRequestErrorMetric(eq("someBidder"), eq(MetricName.unknown_error));
    }

    @Test
    public void shouldPassResponseToPostProcessor() {
        // given
        final BidRequest bidRequest = givenBidRequest(emptyList());

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        verify(bidResponsePostProcessor).postProcess(any(), same(uidsCookie), same(bidRequest), any());
    }

    @Test
    public void shouldReturnBidsWithAdjustedPricesWhenAdjustmentFactorPresent() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenSeatBid(singletonList(
                givenBid(Bid.builder().price(BigDecimal.valueOf(2)).build()))));

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .aliases(emptyMap())
                        .bidadjustmentfactors(singletonMap("bidder", BigDecimal.valueOf(2.468)))
                        .build()))));

        givenBidResponseCreator(singletonList(Bid.builder().price(BigDecimal.valueOf(4.936)).build()));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(BigDecimal.valueOf(4.936));
    }

    @Test
    public void shouldReturnBidsWithoutAdjustingPricesWhenAdjustmentFactorNotPresentForBidder() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);

        givenBidder("bidder", bidder, givenSeatBid(singletonList(
                givenBid(Bid.builder().price(BigDecimal.ONE).build()))));

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .aliases(emptyMap())
                        .bidadjustmentfactors(singletonMap("some-other-bidder", BigDecimal.TEN))
                        .build()))));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(BigDecimal.ONE);
    }

    private AuctionContext givenRequestContext(BidRequest bidRequest) {
        return AuctionContext.builder()
                .uidsCookie(uidsCookie)
                .bidRequest(bidRequest)
                .requestTypeMetric(MetricName.openrtb2web)
                .timeout(timeout)
                .build();
    }

    private static CacheServiceResult givenCacheServiceResult(Bid bid, String cacheId, String videoCacheId) {
        return CacheServiceResult.of(null, null, singletonMap(bid, CacheIdInfo.of(cacheId, videoCacheId)));
    }

    private BidRequest captureBidRequest() {
        final ArgumentCaptor<BidRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpBidderRequester).requestBids(any(), bidRequestCaptor.capture(), any(), anyBoolean());
        return bidRequestCaptor.getValue();
    }

    private static BidRequest givenBidRequest(
            List<Imp> imp, Function<BidRequestBuilder, BidRequestBuilder> bidRequestBuilderCustomizer) {
        return bidRequestBuilderCustomizer.apply(BidRequest.builder().cur(singletonList("USD")).imp(imp)).build();
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

    private void givenBidder(BidderSeatBid response) {
        given(bidderCatalog.isValidName(anyString())).willReturn(true);
        given(httpBidderRequester.requestBids(any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(response));
    }

    private void givenBidder(String bidderName, Bidder<?> bidder, BidderSeatBid response) {
        given(bidderCatalog.isValidName(eq(bidderName))).willReturn(true);
        doReturn(bidder).when(bidderCatalog).bidderByName(eq(bidderName));
        given(httpBidderRequester.requestBids(same(bidder), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(response));
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
        return BidderBid.of(bid, BidType.banner, null);
    }

    private static Bid givenBid(Function<Bid.BidBuilder, Bid.BidBuilder> bidBuilder) {
        return bidBuilder.apply(Bid.builder()
                .id("bidId")
                .price(BigDecimal.ONE)
                .ext(mapper.valueToTree(ExtPrebid.of(ExtBidPrebid.of(null, null, null, null), null))))
                .build();
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

    private static BidderInfo givenBidderInfo(int gdprVendorId, boolean enforceGdpr) {
        return new BidderInfo(true, null, null, null, new BidderInfo.GdprInfo(gdprVendorId, enforceGdpr));
    }

    private static ExtRequestTargeting givenTargeting() {
        return ExtRequestTargeting.of(Json.mapper.valueToTree(
                ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                        BigDecimal.valueOf(0.5))))), null, null, true, true);
    }

    private void givenBidResponseCreator(List<Bid> bids) {
        given(bidResponseCreator.create(anyList(), any(), any(), any(), any(), anyMap(),
                anyBoolean())).willReturn(givenBidResponseWithBids(bids, null));
    }

    private void givenBidResponseCreator(List<Bid> bids, ExtBidResponse extBidResponse) {
        given(bidResponseCreator.create(anyList(), any(), any(), any(), any(), anyMap(),
                anyBoolean())).willReturn(givenBidResponseWithBids(bids, extBidResponse));
    }

    private void givenBidResponseCreator(Map<String, List<ExtBidderError>> errors) {
        given(bidResponseCreator.create(anyList(), any(), any(), any(), any(), anyMap(),
                anyBoolean())).willReturn(givenBidResponseWithError(errors));
    }

    private static BidResponse givenBidResponseWithBids(List<Bid> bids, ExtBidResponse extBidResponse) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(givenSeatBid(bids, identity())))
                .ext(mapper.valueToTree(extBidResponse))
                .build();
    }

    private static SeatBid givenSeatBid(List<Bid> bids,
                                        Function<SeatBid.SeatBidBuilder, SeatBid.SeatBidBuilder> seatBidCustomizer) {
        return seatBidCustomizer.apply(SeatBid.builder()
                .seat("someBidder")
                .bid(bids))
                .build();
    }

    private static BidResponse givenBidResponseWithError(Map<String, List<ExtBidderError>> errors) {
        return BidResponse.builder()
                .seatbid(emptyList())
                .ext(mapper.valueToTree(ExtBidResponse.of(null, errors, null, null, null)))
                .build();
    }
}
