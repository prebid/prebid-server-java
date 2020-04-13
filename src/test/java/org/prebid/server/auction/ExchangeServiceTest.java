package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BidRequest.BidRequestBuilder;
import com.iab.openrtb.request.Device;
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
import org.prebid.server.auction.model.BidRequestCacheInfo;
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
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.currency.CurrencyConversionService;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRequestCurrency;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheBids;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheVastxml;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchain;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserPrebid;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidderError;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.settings.model.Account;
import org.prebid.server.validation.ResponseBidValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
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
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
        given(bidResponseCreator.create(anyList(), any(), any(), any(), any(), anyBoolean(), anyLong(), anyBoolean(),
                any()))
                .willReturn(Future.succeededFuture(givenBidResponseWithBids(singletonList(givenBid(identity())))));

        given(bidderCatalog.isValidName(anyString())).willReturn(true);
        given(bidderCatalog.isActive(anyString())).willReturn(true);
        given(bidderCatalog.usersyncerByName(anyString())).willReturn(usersyncer);

        given(privacyEnforcementService.mask(argThat(MapUtils::isNotEmpty), any(), any(), any(), any(), any(), any()))
                .willAnswer(inv ->
                        Future.succeededFuture(((Map<String, User>) inv.getArgument(0)).entrySet().stream()
                                .collect(HashMap::new, (map, bidderToUserEntry) -> map.put(bidderToUserEntry.getKey(),
                                        PrivacyEnforcementResult.of(bidderToUserEntry.getValue(), null)),
                                        HashMap::putAll)));

        given(privacyEnforcementService.mask(argThat(MapUtils::isEmpty), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(emptyMap()));

        given(responseBidValidator.validate(any())).willReturn(ValidationResult.success());
        given(usersyncer.getCookieFamilyName()).willReturn("cookieFamily");

        given(currencyService.convertCurrency(any(), any(), any(), any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(storedResponseProcessor.getStoredResponseResult(any(), any(), any()))
                .willAnswer(inv -> Future.succeededFuture(StoredResponseResult.of(inv.getArgument(0), emptyList())));
        given(storedResponseProcessor.mergeWithBidderResponses(any(), any(), any())).willAnswer(
                inv -> inv.getArgument(0));

        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        timeout = new TimeoutFactory(clock).create(500);

        exchangeService = new ExchangeService(
                0,
                bidderCatalog,
                storedResponseProcessor,
                privacyEnforcementService,
                httpBidderRequester,
                responseBidValidator,
                currencyService,
                bidResponseCreator,
                bidResponsePostProcessor,
                metrics,
                clock,
                jacksonMapper);
    }

    @Test
    public void creationShouldFailOnNegativeExpectedCacheTime() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ExchangeService(
                        -1,
                        bidderCatalog,
                        storedResponseProcessor,
                        privacyEnforcementService,
                        httpBidderRequester,
                        responseBidValidator,
                        currencyService,
                        bidResponseCreator,
                        bidResponsePostProcessor,
                        metrics,
                        clock,
                        jacksonMapper));
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
    public void shouldExtractRequestWithCurrencyRatesExtension() {
        // given
        givenBidder(givenEmptySeatBid());

        final Map<String, Map<String, BigDecimal>> currencyRates = doubleMap(
                "GBP", singletonMap("EUR", BigDecimal.valueOf(1.15)),
                "UAH", singletonMap("EUR", BigDecimal.valueOf(1.1565)));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                givenImp(doubleMap("prebid", 0, "someBidder", 1), builder -> builder
                        .id("impId")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(300).build()))
                                .build()))),
                builder -> builder
                        .id("requestId")
                        .ext(mapper.valueToTree(
                                ExtRequestPrebid.builder().currency(ExtRequestCurrency.of(currencyRates)).build()))
                        .tmax(500L));

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
                .ext(mapper.valueToTree(
                        ExtRequestPrebid.builder().currency(ExtRequestCurrency.of(currencyRates)).build()))
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
    public void shouldPassRequestWithExtPrebidToDefinedBidder() {
        // given
        final String bidder1Name = "bidder1";
        final String bidder2Name = "bidder2";
        final Bidder<?> bidder1 = mock(Bidder.class);
        final Bidder<?> bidder2 = mock(Bidder.class);
        givenBidder(bidder1Name, bidder1, givenEmptySeatBid());
        givenBidder(bidder2Name, bidder2, givenEmptySeatBid());

        final ObjectNode extBidRequest = mapper.valueToTree(ExtBidRequest.of(
                ExtRequestPrebid.builder()
                        .bidders(mapper.createObjectNode()
                                .putPOJO(bidder1Name, mapper.createObjectNode().put("test1", "test1"))
                                .putPOJO(bidder2Name, mapper.createObjectNode().put("test2", "test2"))
                                .putPOJO("spam", mapper.createObjectNode().put("spam", "spam")))
                        .auctiontimestamp(1000L)
                        .build()));

        final BidRequest bidRequest = givenBidRequest(asList(
                givenImp(singletonMap(bidder1Name, 1), identity()),
                givenImp(singletonMap(bidder2Name, 2), identity())),
                builder -> builder.ext(extBidRequest));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidRequest> bidRequest1Captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpBidderRequester).requestBids(same(bidder1), bidRequest1Captor.capture(), any(), anyBoolean());

        final BidRequest capturedBidRequest1 = bidRequest1Captor.getValue();
        final JsonNode prebid1 = capturedBidRequest1.getExt().get("prebid");
        assertThat(prebid1).isNotNull();
        final JsonNode bidders1 = prebid1.get("bidders");
        assertThat(bidders1).isNotNull();
        assertThat(bidders1.fields()).hasSize(1)
                .containsOnly(entry("bidder", mapper.createObjectNode().put("test1", "test1")));

        final ArgumentCaptor<BidRequest> bidRequest2Captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpBidderRequester).requestBids(same(bidder2), bidRequest2Captor.capture(), any(), anyBoolean());
        final BidRequest capturedBidRequest2 = bidRequest2Captor.getValue();
        final JsonNode prebid2 = capturedBidRequest2.getExt().get("prebid");
        assertThat(prebid2).isNotNull();
        final JsonNode bidders2 = prebid2.get("bidders");
        assertThat(bidders2).isNotNull();
        assertThat(bidders2.fields()).hasSize(1)
                .containsOnly(entry("bidder", mapper.createObjectNode().put("test2", "test2")));
    }

    @Test
    public void shouldPassRequestWithInjectedSchainInSourceExt() {
        // given
        final String bidder1Name = "bidder1";
        final String bidder2Name = "bidder2";
        final String bidder3Name = "bidder3";
        final Bidder<?> bidder1 = mock(Bidder.class);
        final Bidder<?> bidder2 = mock(Bidder.class);
        final Bidder<?> bidder3 = mock(Bidder.class);
        givenBidder(bidder1Name, bidder1, givenEmptySeatBid());
        givenBidder(bidder2Name, bidder2, givenEmptySeatBid());
        givenBidder(bidder3Name, bidder3, givenEmptySeatBid());

        final ObjectNode schainObject = mapper.createObjectNode().put("var", "1");
        final ObjectNode allSchainObject = mapper.createObjectNode().put("any", "any");
        final ExtRequestPrebidSchain schain1 = ExtRequestPrebidSchain.of(Arrays.asList(bidder1Name, bidder2Name),
                schainObject);
        final ExtRequestPrebidSchain allSchain = ExtRequestPrebidSchain.of(singletonList("*"), allSchainObject);
        final ObjectNode extBidRequest = mapper.valueToTree(ExtBidRequest.of(
                ExtRequestPrebid.builder()
                        .schains(Arrays.asList(schain1, allSchain))
                        .auctiontimestamp(1000L)
                        .build()));

        final BidRequest bidRequest = givenBidRequest(asList(
                givenImp(singletonMap(bidder1Name, 1), identity()),
                givenImp(singletonMap(bidder2Name, 2), identity()),
                givenImp(singletonMap(bidder3Name, 3), identity())),
                builder -> builder.ext(extBidRequest));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidRequest> bidRequest1Captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpBidderRequester).requestBids(same(bidder1), bidRequest1Captor.capture(), any(), anyBoolean());
        final BidRequest capturedBidRequest1 = bidRequest1Captor.getValue();
        final JsonNode requestSchain1 = capturedBidRequest1.getSource().getExt().get("schain");
        assertThat(requestSchain1).isNotNull();
        assertThat(requestSchain1).isEqualTo(schainObject);
        assertThat(capturedBidRequest1.getExt().get("prebid").get("schains")).isNull();

        final ArgumentCaptor<BidRequest> bidRequest2Captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpBidderRequester).requestBids(same(bidder2), bidRequest2Captor.capture(), any(), anyBoolean());
        final BidRequest capturedBidRequest2 = bidRequest2Captor.getValue();
        final JsonNode requestSchain2 = capturedBidRequest2.getSource().getExt().get("schain");
        assertThat(requestSchain2).isNotNull();
        assertThat(requestSchain2).isEqualTo(schainObject);
        assertThat(capturedBidRequest2.getExt().get("prebid").get("schains")).isNull();

        final ArgumentCaptor<BidRequest> bidRequest3Captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpBidderRequester).requestBids(same(bidder3), bidRequest3Captor.capture(), any(), anyBoolean());
        final BidRequest capturedBidRequest3 = bidRequest3Captor.getValue();
        final JsonNode requestSchain3 = capturedBidRequest3.getSource().getExt().get("schain");
        assertThat(requestSchain3).isNotNull();
        assertThat(requestSchain3).isEqualTo(allSchainObject);
        assertThat(capturedBidRequest3.getExt().get("prebid").get("schains")).isNull();
    }

    @Test
    public void shouldReturnFailedFutureWithUnchangedMessageWhenPrivacyEnforcementServiceFails() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());

        given(privacyEnforcementService.mask(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.failedFuture("Error when retrieving allowed purpose ids"));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .regs(Regs.of(null, mapper.valueToTree(ExtRegs.of(1, null)))));

        // when
        final Future<?> result = exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).hasMessage("Error when retrieving allowed purpose ids");
    }

    @Test
    public void shouldReturnFailedFutureWithPrebidExceptionIfExtRegsCannotBeParsed() {
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
                        .aliases(singletonMap("bidderAlias", "bidder"))
                        .auctiontimestamp(1000L)
                        .build()))));

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
                        .aliases(singletonMap("bidderAlias", "bidder")).auctiontimestamp(1000L).build()))));

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
        given(httpBidderRequester.requestBids(any(), eq(givenBidRequest(givenSingleImp(singletonMap("bidder", 1)),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .auctiontimestamp(1000L)
                        .aliases(singletonMap("bidderAlias", "bidder")).build()))))), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(singletonList(
                        givenBid(Bid.builder().price(BigDecimal.ONE).build())))));

        given(httpBidderRequester.requestBids(any(), eq(givenBidRequest(givenSingleImp(singletonMap("bidder", 2)),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .auctiontimestamp(1000L)
                        .aliases(singletonMap("bidderAlias", "bidder")).build()))))), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(singletonList(
                        givenBid(Bid.builder().price(BigDecimal.ONE).build())))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(doubleMap("bidder", 1, "bidderAlias", 2)),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("bidderAlias", "bidder"))
                        .auctiontimestamp(1000L)
                        .build()))));

        given(bidResponseCreator.create(anyList(), any(), any(), any(), any(), anyBoolean(), anyLong(), anyBoolean(),
                any()))
                .willReturn(Future.succeededFuture(BidResponse.builder()
                        .seatbid(asList(
                                givenSeatBid(singletonList(givenBid(identity())), identity()),
                                givenSeatBid(singletonList(givenBid(identity())), identity())))
                        .build()));

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        verify(httpBidderRequester, times(2)).requestBids(any(), any(), any(), anyBoolean());
        assertThat(bidResponse.getSeatbid()).hasSize(2)
                .extracting(seatBid -> seatBid.getBid().size())
                .containsOnly(1, 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldCallBidResponseCreatorWithExpectedParams() {
        // given
        givenBidder("bidder1", mock(Bidder.class), givenEmptySeatBid());

        final Bid thirdBid = Bid.builder().id("bidId3").impid("impId1").price(BigDecimal.valueOf(7.89)).build();
        givenBidder("bidder2", mock(Bidder.class), givenSeatBid(singletonList(givenBid(thirdBid))));

        final ExtRequestTargeting targeting = givenTargeting(true);
        final ObjectNode events = mapper.createObjectNode();
        final BidRequest bidRequest = givenBidRequest(asList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1")),
                givenImp(doubleMap("bidder1", 1, "bidder2", 2), builder -> builder.id("impId1"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(targeting)
                        .auctiontimestamp(1000L)
                        .events(events)
                        .cache(ExtRequestPrebidCache.of(ExtRequestPrebidCacheBids.of(53, true),
                                ExtRequestPrebidCacheVastxml.of(34, true), true))
                        .build()))));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        final BidRequestCacheInfo expectedCacheInfo = BidRequestCacheInfo.builder()
                .doCaching(true)
                .shouldCacheBids(true)
                .shouldCacheVideoBids(true)
                .returnCreativeBids(true)
                .returnCreativeVideoBids(true)
                .cacheBidsTtl(53)
                .cacheVideoBidsTtl(34)
                .shouldCacheWinningBidsOnly(false)
                .build();

        final ArgumentCaptor<List<BidderResponse>> captor = ArgumentCaptor.forClass(List.class);
        verify(bidResponseCreator).create(
                captor.capture(),
                eq(bidRequest),
                eq(targeting),
                eq(expectedCacheInfo),
                eq(Account.builder().id("accountId").eventsEnabled(true).build()),
                eq(true),
                eq(1000L), eq(false), eq(timeout));

        assertThat(captor.getValue()).containsOnly(
                BidderResponse.of("bidder2", BidderSeatBid.of(singletonList(
                        BidderBid.of(thirdBid, banner, null)), emptyList(), emptyList()), 0),
                BidderResponse.of("bidder1", BidderSeatBid.of(emptyList(), emptyList(), emptyList()), 0));
    }

    @Test
    public void shouldCallBidResponseCreatorWithWinningOnlyTrueWhenIncludeBidderKeysIsFalse() {
        // given
        givenBidder("bidder1", mock(Bidder.class), givenEmptySeatBid());

        final Bid thirdBid = Bid.builder().id("bidId3").impid("impId1").price(BigDecimal.valueOf(7.89)).build();
        givenBidder("bidder2", mock(Bidder.class), givenSeatBid(singletonList(givenBid(thirdBid))));

        final ExtRequestTargeting targeting = givenTargeting(false);

        final BidRequest bidRequest = givenBidRequest(asList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1")),
                givenImp(doubleMap("bidder1", 1, "bidder2", 2), builder -> builder.id("impId1"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(targeting)
                        .cache(ExtRequestPrebidCache.of(null, null, true))
                        .auctiontimestamp(1000L)
                        .build()))));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        verify(bidResponseCreator).create(
                anyList(),
                eq(bidRequest),
                eq(targeting),
                eq(BidRequestCacheInfo.builder().doCaching(true).shouldCacheWinningBidsOnly(true).build()),
                any(), eq(false), anyLong(), eq(false), any());
    }

    @Test
    public void shouldCallBidResponseCreatorWithWinningOnlyFalseWhenWinningOnlyIsNull() {
        // given
        givenBidder("bidder1", mock(Bidder.class), givenEmptySeatBid());

        final Bid thirdBid = Bid.builder().id("bidId3").impid("impId1").price(BigDecimal.valueOf(7.89)).build();
        givenBidder("bidder2", mock(Bidder.class), givenSeatBid(singletonList(givenBid(thirdBid))));

        final ExtRequestTargeting targeting = givenTargeting(false);

        final BidRequest bidRequest = givenBidRequest(asList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1")),
                givenImp(doubleMap("bidder1", 1, "bidder2", 2), builder -> builder.id("impId1"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(targeting)
                        .cache(ExtRequestPrebidCache.of(null, null, null))
                        .auctiontimestamp(1000L)
                        .build()))));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        verify(bidResponseCreator).create(
                anyList(),
                any(),
                eq(targeting),
                eq(BidRequestCacheInfo.builder().build()),
                any(), anyBoolean(), anyLong(), anyBoolean(), any());
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
        verify(bidResponseCreator).create(anyList(), eq(bidRequest), any(), any(), any(), anyBoolean(), anyLong(),
                eq(true), any());
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
                        mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder().debug(1).auctiontimestamp(1000L)
                                .build()))));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        verify(bidResponseCreator).create(anyList(), eq(bidRequest), any(), any(), any(), eq(false), anyLong(),
                eq(true), any());
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
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(singletonList("someBidder"))).auctiontimestamp(1000L).build()))));

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
                        .auctiontimestamp(1000L)
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
        assertThat(ext.getErrors()).hasSize(1)
                .containsOnly(entry("bidder1", bidderErrors));
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
                                .build()))),
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
        final BidResponse bidResponse = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
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

        // this is not required but stated for clarity's sake. The case when bidder is disabled.
        given(bidderCatalog.isActive(anyString())).willReturn(false);
        given(uidsCookie.uidFrom(any())).willReturn(null);

        final User user = User.builder().id("userId").build();
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.user(user));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        verify(uidsCookie).uidFrom(isNull());

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
    public void shouldNotChangeGdprFromRequestWhenDeviceLmtIsOne() {
        // given
        givenBidder(givenEmptySeatBid());

        given(uidsCookie.uidFrom(anyString())).willReturn("buyeridFromCookie");

        final Regs regs = Regs.of(null, null);
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.user(User.builder().build())
                        .device(Device.builder().lmt(1).build())
                        .regs(regs));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final Regs capturedRegs = captureBidRequest().getRegs();
        assertThat(capturedRegs).isSameAs(regs);
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
                        .auctiontimestamp(1000L)
                        .build()))));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final ObjectNode capturedBidRequestExt = captureBidRequest().getExt();
        assertThat(capturedBidRequestExt).isEqualTo(mapper.valueToTree(
                ExtBidRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("someBidder", "alias_should_stay"))
                        .data(ExtRequestPrebidData.of(singletonList("someBidder")))
                        .auctiontimestamp(1000L)
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
                                .auctiontimestamp(1000L)
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
                .extracting(User::getExt)
                .containsOnly(
                        mapper.valueToTree(ExtUser.builder().data(dataNode).build()),
                        mapper.createObjectNode());
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
                        .auctiontimestamp(1000L)
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
                .extracting(Site::getExt)
                .containsOnly(
                        mapper.valueToTree(ExtSite.of(0, dataNode)),
                        mapper.valueToTree(ExtSite.of(0, null)));
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
                        .data(ExtRequestPrebidData.of(singletonList("someBidder"))).auctiontimestamp(1000L).build())))
                        .app(App.builder().ext(mapper.valueToTree(ExtApp.of(null, dataNode))).build()));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpBidderRequester, times(2)).requestBids(any(), bidRequestCaptor.capture(), any(), anyBoolean());
        final List<BidRequest> capturedBidRequests = bidRequestCaptor.getAllValues();

        assertThat(capturedBidRequests)
                .extracting(BidRequest::getApp)
                .extracting(App::getExt)
                .containsOnly(
                        mapper.valueToTree(ExtApp.of(null, dataNode)),
                        mapper.createObjectNode());
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
    public void shouldPassReducedGlobalTimeoutToConnectorAndOriginalToBidResponseCreator() {
        // given
        exchangeService = new ExchangeService(
                100,
                bidderCatalog,
                storedResponseProcessor,
                privacyEnforcementService,
                httpBidderRequester,
                responseBidValidator,
                currencyService,
                bidResponseCreator,
                bidResponsePostProcessor,
                metrics,
                clock,
                jacksonMapper);

        final Bid bid = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        givenBidder(givenSeatBid(singletonList(givenBid(bid))));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(givenTargeting(true))
                        .cache(ExtRequestPrebidCache.of(ExtRequestPrebidCacheBids.of(null, null), null, null))
                        .auctiontimestamp(1000L)
                        .build()))));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        final ArgumentCaptor<Timeout> timeoutCaptor = ArgumentCaptor.forClass(Timeout.class);
        verify(httpBidderRequester).requestBids(any(), any(), timeoutCaptor.capture(), anyBoolean());
        assertThat(timeoutCaptor.getValue().remaining()).isEqualTo(400L);
        verify(bidResponseCreator).create(anyList(), any(), any(), any(), any(), anyBoolean(), anyLong(), anyBoolean(),
                same(timeout));
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

    @SuppressWarnings("unchecked")
    @Test
    public void shouldDropBidIfPrebidExceptionWasThrownDuringCurrencyConversion() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenSeatBid(singletonList(
                givenBid(Bid.builder().price(BigDecimal.valueOf(2.0)).build(), "CUR"))));

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                identity());

        given(currencyService.convertCurrency(any(), any(), any(), any()))
                .willThrow(new PreBidException("no currency conversion available"));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        final ArgumentCaptor<List<BidderResponse>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(bidResponseCreator).create(argumentCaptor.capture(), any(), any(), any(), any(), anyBoolean(), anyLong(),
                anyBoolean(), any());

        assertThat(argumentCaptor.getValue()).hasSize(1);

        final BidderError expectedError = BidderError.generic("Unable to covert bid currency CUR to desired ad"
                + " server currency USD. no currency conversion available");
        final BidderSeatBid firstSeatBid = argumentCaptor.getValue().get(0).getSeatBid();
        assertThat(firstSeatBid.getBids()).isEmpty();
        assertThat(firstSeatBid.getErrors()).containsOnly(expectedError);
    }

    @SuppressWarnings("unchecked")
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
                        .auctiontimestamp(1000L)
                        .build()))));

        given(currencyService.convertCurrency(any(), any(), any(), any())).willReturn(BigDecimal.valueOf(10.0));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        final ArgumentCaptor<List<BidderResponse>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(bidResponseCreator).create(argumentCaptor.capture(), any(), any(), any(), any(), anyBoolean(), anyLong(),
                anyBoolean(), any());

        assertThat(argumentCaptor.getValue()).hasSize(1);

        final BigDecimal updatedPrice = BigDecimal.valueOf(100);
        final BidderSeatBid firstSeatBid = argumentCaptor.getValue().get(0).getSeatBid();
        assertThat(firstSeatBid.getBids())
                .extracting(BidderBid::getBid)
                .flatExtracting(Bid::getPrice)
                .containsOnly(updatedPrice);
        assertThat(firstSeatBid.getErrors()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldUpdatePriceForOneBidAndDropAnotherIfPrebidExceptionHappensForSecondBid() {
        // given
        final BigDecimal firstBidderPrice = BigDecimal.valueOf(2.0);
        final BigDecimal secondBidderPrice = BigDecimal.valueOf(3.0);
        givenBidder("bidder", mock(Bidder.class), givenSeatBid(asList(
                givenBid(Bid.builder().price(firstBidderPrice).build(), "CUR1"),
                givenBid(Bid.builder().price(secondBidderPrice).build(), "CUR2"))));

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                identity());

        final BigDecimal updatedPrice = BigDecimal.valueOf(10.0);
        given(currencyService.convertCurrency(any(), any(), any(), any())).willReturn(updatedPrice)
                .willThrow(new PreBidException("no currency conversion available"));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        final ArgumentCaptor<List<BidderResponse>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(bidResponseCreator).create(argumentCaptor.capture(), any(), any(), any(), any(), anyBoolean(), anyLong(),
                anyBoolean(), any());
        verify(currencyService).convertCurrency(eq(firstBidderPrice), eq(null), any(), eq("CUR1"));
        verify(currencyService).convertCurrency(eq(secondBidderPrice), eq(null), any(), eq("CUR2"));

        assertThat(argumentCaptor.getValue()).hasSize(1);

        final Bid expectedBid = Bid.builder().price(updatedPrice).build();
        final BidderBid expectedBidderBid = BidderBid.of(expectedBid, banner, "CUR1");
        final BidderError expectedError = BidderError.generic("Unable to covert bid currency CUR2 to desired ad"
                + " server currency USD. no currency conversion available");

        final BidderSeatBid firstSeatBid = argumentCaptor.getValue().get(0).getSeatBid();
        assertThat(firstSeatBid.getBids()).containsOnly(expectedBidderBid);
        assertThat(firstSeatBid.getErrors()).containsOnly(expectedError);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldRespondWithOneBidAndErrorWhenBidResponseContainsOneUnsupportedCurrency() {
        // given
        final BigDecimal firstBidderPrice = BigDecimal.valueOf(2.0);
        final BigDecimal secondBidderPrice = BigDecimal.valueOf(10.0);
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().price(firstBidderPrice).build(), "USD"))));
        givenBidder("bidder2", mock(Bidder.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().price(BigDecimal.valueOf(10.0)).build(), "CUR"))));

        final BidRequest bidRequest = BidRequest.builder().cur(Collections.singletonList("BAD"))
                .imp(singletonList(givenImp(doubleMap("bidder1", 2, "bidder2", 3),
                        identity()))).build();

        final BigDecimal updatedPrice = BigDecimal.valueOf(20);
        given(currencyService.convertCurrency(any(), any(), any(), any())).willReturn(updatedPrice);
        given(currencyService.convertCurrency(any(), any(), eq("BAD"), eq("CUR")))
                .willThrow(new PreBidException("no currency conversion available"));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        final ArgumentCaptor<List<BidderResponse>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(bidResponseCreator).create(argumentCaptor.capture(), any(), any(), any(), any(), anyBoolean(), anyLong(),
                anyBoolean(), any());
        verify(currencyService).convertCurrency(eq(firstBidderPrice), eq(null), eq("BAD"), eq("USD"));
        verify(currencyService).convertCurrency(eq(secondBidderPrice), eq(null), eq("BAD"), eq("CUR"));

        assertThat(argumentCaptor.getValue()).hasSize(2);

        final Bid expectedBid = Bid.builder().price(updatedPrice).build();
        final BidderBid expectedBidderBid = BidderBid.of(expectedBid, banner, "USD");
        assertThat(argumentCaptor.getValue())
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .containsOnly(expectedBidderBid);

        final BidderError expectedError = BidderError.generic("Unable to covert bid currency CUR to desired ad"
                + " server currency BAD. no currency conversion available");
        assertThat(argumentCaptor.getValue())
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getErrors)
                .containsOnly(expectedError);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldUpdateBidPriceWithCurrencyConversionAndAddErrorAboutMultipleCurrency() {
        // given
        final BigDecimal bidderPrice = BigDecimal.valueOf(2.0);
        givenBidder("bidder", mock(Bidder.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().price(bidderPrice).build(), "USD"))));

        final BidRequest bidRequest = givenBidRequest(
                singletonList(givenImp(singletonMap("bidder", 2), identity())),
                builder -> builder.cur(Arrays.asList("CUR1", "CUR2", "CUR2")));

        final BigDecimal updatedPrice = BigDecimal.valueOf(10.0);
        given(currencyService.convertCurrency(any(), any(), any(), any())).willReturn(updatedPrice);

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        final ArgumentCaptor<List<BidderResponse>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(bidResponseCreator).create(argumentCaptor.capture(), any(), any(), any(), any(), anyBoolean(), anyLong(),
                anyBoolean(), any());
        verify(currencyService).convertCurrency(eq(bidderPrice), eq(null), eq("CUR1"), eq("USD"));

        assertThat(argumentCaptor.getValue()).hasSize(1);

        final BidderError expectedError = BidderError.badInput("Cur parameter contains more than one currency."
                + " CUR1 will be used");
        final BidderSeatBid firstSeatBid = argumentCaptor.getValue().get(0).getSeatBid();
        assertThat(firstSeatBid.getBids())
                .extracting(BidderBid::getBid)
                .flatExtracting(Bid::getPrice)
                .containsOnly(updatedPrice);
        assertThat(firstSeatBid.getErrors()).containsOnly(expectedError);
    }

    @SuppressWarnings("unchecked")
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

        final BigDecimal updatedPrice = BigDecimal.valueOf(10.0);
        given(currencyService.convertCurrency(any(), any(), any(), any())).willReturn(updatedPrice);
        given(currencyService.convertCurrency(any(), any(), any(), eq("USD"))).willReturn(bidder3Price);

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        final ArgumentCaptor<List<BidderResponse>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(bidResponseCreator).create(argumentCaptor.capture(), any(), any(), any(), any(), anyBoolean(), anyLong(),
                anyBoolean(), any());
        verify(currencyService).convertCurrency(eq(bidder1Price), eq(null), eq("USD"), eq("EUR"));
        verify(currencyService).convertCurrency(eq(bidder2Price), eq(null), eq("USD"), eq("GBP"));
        verify(currencyService).convertCurrency(eq(bidder3Price), eq(null), eq("USD"), eq("USD"));
        verifyNoMoreInteractions(currencyService);

        assertThat(argumentCaptor.getValue())
                .hasSize(3)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsOnly(bidder3Price, updatedPrice, updatedPrice);
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
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.app(App.builder().build()));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        verify(metrics).updateAdapterRequestTypeAndNoCookieMetrics(
                eq("someBidder"), eq(MetricName.openrtb2web), eq(false));
    }

    @Test
    public void shouldUseEmptyStringIfPublisherIdIsEmpty() {
        // given
        given(httpBidderRequester.requestBids(any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(singletonList(
                        givenBid(Bid.builder().price(TEN).build())))));
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));
        final Account account = Account.builder().id("").build();

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest, account));

        // then
        verify(metrics).updateAccountRequestMetrics(eq(""), eq(MetricName.openrtb2web));
    }

    @Test
    public void shouldIncrementNoBidRequestsMetric() {
        // given
        given(httpBidderRequester.requestBids(any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(emptyList())));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

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

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

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
        verify(bidResponsePostProcessor).postProcess(any(), same(uidsCookie), same(bidRequest), any(),
                eq(Account.builder().id("accountId").eventsEnabled(true).build()));
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
                        .auctiontimestamp(1000L)
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
                        .auctiontimestamp(1000L)
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
        return givenRequestContext(bidRequest, Account.builder().id("accountId").eventsEnabled(true).build());
    }

    private AuctionContext givenRequestContext(BidRequest bidRequest, Account account) {
        return AuctionContext.builder()
                .uidsCookie(uidsCookie)
                .bidRequest(bidRequest)
                .account(account)
                .requestTypeMetric(MetricName.openrtb2web)
                .timeout(timeout)
                .build();
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
        return impBuilderCustomizer.apply(Imp.builder()
                .ext(ext != null ? mapper.valueToTree(ext) : null))
                .build();
    }

    private static <T> List<Imp> givenSingleImp(T ext) {
        return singletonList(givenImp(ext, identity()));
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

    private static SeatBid givenSeatBid(List<Bid> bids,
                                        Function<SeatBid.SeatBidBuilder, SeatBid.SeatBidBuilder> seatBidCustomizer) {
        return seatBidCustomizer.apply(SeatBid.builder()
                .seat("someBidder")
                .bid(bids))
                .build();
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

    private static BidderBid givenBid(Bid bid, String cur) {
        return BidderBid.of(bid, BidType.banner, cur);
    }

    private static Bid givenBid(Function<Bid.BidBuilder, Bid.BidBuilder> bidBuilder) {
        return bidBuilder.apply(Bid.builder()
                .id("bidId")
                .price(BigDecimal.ONE)
                .ext(mapper.valueToTree(ExtPrebid.of(ExtBidPrebid.of(null, null, null, null, null, null), null))))
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

    private static ExtRequestTargeting givenTargeting(boolean includebidderkeys) {
        return ExtRequestTargeting.builder().pricegranularity(mapper.valueToTree(
                ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                        BigDecimal.valueOf(0.5))))))
                .includewinners(true)
                .includebidderkeys(includebidderkeys)
                .build();
    }

    private void givenBidResponseCreator(List<Bid> bids) {
        given(bidResponseCreator.create(anyList(), any(), any(), any(), any(), anyBoolean(), anyLong(), anyBoolean(),
                any()))
                .willReturn(Future.succeededFuture(givenBidResponseWithBids(bids)));
    }

    private void givenBidResponseCreator(Map<String, List<ExtBidderError>> errors) {
        given(bidResponseCreator.create(anyList(), any(), any(), any(), any(), anyBoolean(), anyLong(), anyBoolean(),
                any()))
                .willReturn(Future.succeededFuture(givenBidResponseWithError(errors)));
    }

    private static BidResponse givenBidResponseWithBids(List<Bid> bids) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(givenSeatBid(bids, identity())))
                .build();
    }

    private static BidResponse givenBidResponseWithError(Map<String, List<ExtBidderError>> errors) {
        return BidResponse.builder()
                .seatbid(emptyList())
                .ext(mapper.valueToTree(ExtBidResponse.of(null, errors, null, null, null, null)))
                .build();
    }
}
