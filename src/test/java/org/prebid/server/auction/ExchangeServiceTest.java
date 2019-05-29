package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BidRequest.BidRequestBuilder;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Imp.ImpBuilder;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
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
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
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
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.events.EventsService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.gdpr.GdprService;
import org.prebid.server.gdpr.model.GdprResponse;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.metric.model.MetricsContext;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheBids;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheVastxml;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserPrebid;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.CacheAsset;
import org.prebid.server.proto.openrtb.ext.response.Events;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidderError;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseCache;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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
    private ResponseBidValidator responseBidValidator;
    @Mock
    private CacheService cacheService;
    @Mock
    private CurrencyConversionService currencyService;
    @Mock
    private GdprService gdprService;
    @Spy
    private BidResponsePostProcessor.NoOpBidResponsePostProcessor bidResponsePostProcessor;
    @Mock
    private Usersyncer usersyncer;
    @Mock
    private Metrics metrics;
    @Mock
    private UidsCookie uidsCookie;
    @Mock
    private HttpBidderRequester httpBidderRequester;
    @Mock
    private EventsService eventsService;

    private Clock clock;

    private ExchangeService exchangeService;

    private Timeout timeout;
    private MetricsContext metricsContext;

    @Before
    public void setUp() {
        given(bidderCatalog.isValidName(anyString())).willReturn(true);
        given(bidderCatalog.isActive(anyString())).willReturn(true);
        given(bidderCatalog.usersyncerByName(anyString())).willReturn(usersyncer);

        given(bidderCatalog.bidderInfoByName(anyString())).willReturn(givenBidderInfo(15, true));

        given(responseBidValidator.validate(any())).willReturn(ValidationResult.success());
        given(usersyncer.getCookieFamilyName()).willReturn("cookieFamily");

        given(currencyService.convertCurrency(any(), any(), any(), any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(gdprService.resultByVendor(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(GdprResponse.of(true, singletonMap(1, true), null)));

        given(eventsService.isEventsEnabled(any(), any())).willReturn(Future.succeededFuture(false));

        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        timeout = new TimeoutFactory(clock).create(500);
        metricsContext = MetricsContext.of(MetricName.openrtb2web);

        exchangeService = new ExchangeService(bidderCatalog, httpBidderRequester, responseBidValidator, cacheService,
                bidResponsePostProcessor, currencyService, gdprService, eventsService, metrics, clock, false, 0);
    }

    @Test
    public void creationShouldFailOnNegativeExpectedCacheTime() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ExchangeService(bidderCatalog, httpBidderRequester, responseBidValidator, cacheService,
                        bidResponsePostProcessor, currencyService, gdprService,
                        eventsService, metrics, clock, false, -1));
    }

    @Test
    public void shouldTolerateImpWithoutExtension() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(null));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

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
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        verify(bidderCatalog).isValidName(eq("invalid"));
        verify(bidderCatalog).isDeprecatedName(eq("invalid"));
        verifyZeroInteractions(httpBidderRequester);
        assertThat(bidResponse).isNotNull();
    }

    @Test
    public void shouldProcessRequestAndAddErrorAboutDeprecatedBidder() {
        // given
        final String invalidBidderName = "invalid";

        given(bidderCatalog.isValidName(invalidBidderName)).willReturn(false);
        given(bidderCatalog.isDeprecatedName(invalidBidderName)).willReturn(true);
        given(bidderCatalog.errorForDeprecatedName(invalidBidderName)).willReturn(
                "invalid has been deprecated and is no longer available. Use valid instead.");

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap(invalidBidderName, 0)));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        assertThat(bidResponse.getExt()).isEqualTo(mapper.valueToTree(ExtBidResponse.of(null,
                Collections.singletonMap(invalidBidderName, Collections.singletonList(
                        ExtBidderError.of(BidderError.Type.bad_input.getCode(),
                                "invalid has been deprecated and is no longer available. Use valid instead."))),
                new HashMap<>(), null, null)));
    }

    @Test
    public void shouldTolerateMissingPrebidImpExtension() {
        // given
        givenBidder(givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

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
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

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
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

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
    public void shouldMaskUserAndDevicePropertiesIfGdprIsEnforcedForBidderAndGdprServiceRespondThatGdprIsEnabled() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());

        final Map<String, String> uids = singletonMap("someBidder", "uidval");

        given(gdprService.resultByVendor(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(GdprResponse.of(true, singletonMap(15, false), null)));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(User.builder().ext(mapper.valueToTree(
                                ExtUser.of(ExtUserPrebid.of(uids), null, null, null, null)))
                                .geo(Geo.builder().lon(-85.1245F).lat(189.9531F).build())
                                .build())
                        .device(Device.builder()
                                .ip("192.168.0.10")
                                .ipv6("2001:0db8:85a3:0000:0000:8a2e:0370:7334")
                                .geo(Geo.builder().lon(-85.34321F).lat(189.342323F).build())
                                .ifa("ifa")
                                .macsha1("macsha1")
                                .macmd5("macmd5")
                                .didsha1("didsha1")
                                .didmd5("didmd5")
                                .dpidsha1("dpidsha1")
                                .dpidmd5("dpidmd5")
                                .build()));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

        // then
        final ArgumentCaptor<BidRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpBidderRequester).requestBids(same(bidder), bidRequestCaptor.capture(), any(), anyBoolean());
        final BidRequest capturedBidRequest = bidRequestCaptor.getValue();
        assertThat(capturedBidRequest.getUser()).isEqualTo(User.builder().buyeruid(null)
                .ext(mapper.valueToTree(ExtUser.of(null, null, null, null, null)))
                .geo(Geo.builder().lon(-85.12F).lat(189.95F).build())
                .build());
        assertThat(capturedBidRequest.getDevice()).isEqualTo(Device.builder().ip("192.168.0.0")
                .ipv6("2001:0db8:85a3:0000:0000:8a2e:0370:0")
                .geo(Geo.builder().lon(-85.34F).lat(189.34F).build()).build());
        assertThat(capturedBidRequest.getRegs()).isEqualTo(Regs.of(null, mapper.valueToTree(ExtRegs.of(1))));
    }

    @Test
    public void shouldNotChangeRequestIfEnforcedForBidderIsTrueAndGdprServiceRespondThatGdprIsNotEnabled() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());

        given(gdprService.resultByVendor(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(GdprResponse.of(true, singletonMap(15, true), null)));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(User.builder().ext(mapper.valueToTree(
                                ExtUser.of(ExtUserPrebid.of(singletonMap("someBidder", "uidval")), null,
                                        null, null, null))).build())
                        .regs(Regs.of(null, mapper.valueToTree(ExtRegs.of(1)))));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

        // then
        final ArgumentCaptor<BidRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpBidderRequester).requestBids(same(bidder), bidRequestCaptor.capture(), any(), anyBoolean());
        final BidRequest capturedBidRequest = bidRequestCaptor.getValue();
        assertThat(capturedBidRequest.getUser()).isEqualTo(User.builder().buyeruid("uidval")
                .ext(mapper.valueToTree(ExtUser.of(null, null, null, null, null))).build());
    }

    @Test
    public void shouldAskGdprServiceWithNullGdprIfAbsentInRequest() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .regs(Regs.of(null, null))); // no ext

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

        // then
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(gdprService).resultByVendor(any(), captor.capture(), any(), any(), any());
        assertThat(captor.getValue()).isNull();
    }

    @Test
    public void shouldNotChangeRequestIfEnforcedForBidderIsFalseAndGdprEqualsToOne() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());

        given(bidderCatalog.bidderInfoByName(anyString())).willReturn(givenBidderInfo(15, false));
        given(gdprService.resultByVendor(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(GdprResponse.of(true, singletonMap(15, false), null)));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(User.builder().ext(mapper.valueToTree(
                                ExtUser.of(ExtUserPrebid.of(singletonMap("someBidder", "uidval")), null,
                                        null, null, null))).build())
                        .device(Device.builder()
                                .ip("192.168.0.1")
                                .ipv6("2001:0db8:85a3:0000:0000:8a2e:0370:7334")
                                .geo(Geo.builder().lon(-85.34321F).lat(189.342323F).build())
                                .ifa("ifa")
                                .macsha1("macsha1")
                                .macmd5("macmd5")
                                .didsha1("didsha1")
                                .didmd5("didmd5")
                                .dpidsha1("dpidsha1")
                                .dpidmd5("dpidmd5")
                                .build())
                        .regs(Regs.of(null, mapper.valueToTree(ExtRegs.of(1)))));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

        // then
        final ArgumentCaptor<BidRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpBidderRequester).requestBids(same(bidder), bidRequestCaptor.capture(), any(), anyBoolean());
        final BidRequest capturedBidRequest = bidRequestCaptor.getValue();
        assertThat(capturedBidRequest.getUser()).isEqualTo(User.builder().buyeruid("uidval")
                .ext(mapper.valueToTree(ExtUser.of(null, null, null, null, null))).build());
        assertThat(capturedBidRequest.getDevice()).isEqualTo(Device.builder().ip("192.168.0.1")
                .ipv6("2001:0db8:85a3:0000:0000:8a2e:0370:7334")
                .geo(Geo.builder().lon(-85.34321F).lat(189.342323F).build())
                .ifa("ifa").macsha1("macsha1").macmd5("macmd5").didsha1("didsha1").didmd5("didmd5").dpidsha1("dpidsha1")
                .dpidmd5("dpidmd5")
                .build());
        verifyZeroInteractions(gdprService);
    }

    @Test
    public void shouldSupportBidderAliasConversionInGdprLookup() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());

        given(gdprService.resultByVendor(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(GdprResponse.of(true, singletonMap(15, false), null)));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidderAlias", 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(User.builder().ext(mapper.valueToTree(
                                ExtUser.of(ExtUserPrebid.of(singletonMap("bidderAlias", "uidval")), null,
                                        null, null, null))).build())
                        .regs(Regs.of(null, mapper.valueToTree(ExtRegs.of(1))))
                        .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                                .aliases(singletonMap("bidderAlias", "someBidder")).build()))));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

        // then
        final ArgumentCaptor<BidRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpBidderRequester).requestBids(same(bidder), bidRequestCaptor.capture(), any(), anyBoolean());
        final BidRequest capturedBidRequest = bidRequestCaptor.getValue();
        assertThat(capturedBidRequest.getUser()).isEqualTo(User.builder().buyeruid(null)
                .ext(mapper.valueToTree(ExtUser.of(null, null, null, null, null))).build());
    }

    @Test
    public void shouldApplyGdprMaskingRulesForBiddersWithDifferentPbsEnforcesGdprAndGdprServiceResponse() {
        // given
        givenBidder("bidder1", mock(Bidder.class), givenEmptySeatBid());
        givenBidder("bidder2", mock(Bidder.class), givenEmptySeatBid());
        givenBidder("bidder3", mock(Bidder.class), givenEmptySeatBid());

        given(bidderCatalog.bidderInfoByName("bidder1")).willReturn(givenBidderInfo(1, false));
        given(bidderCatalog.bidderInfoByName("bidder2")).willReturn(givenBidderInfo(2, true));
        given(bidderCatalog.bidderInfoByName("bidder3")).willReturn(givenBidderInfo(3, true));

        given(gdprService.resultByVendor(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(GdprResponse.of(true, doubleMap(2, true, 3, false), null)));

        final Map<String, String> uids = new HashMap<>();
        uids.put("bidder1", "uid1");
        uids.put("bidder2", "uid2");
        uids.put("bidder3", "uid3");

        final BidRequest bidRequest = givenBidRequest(asList(
                givenImp(singletonMap("bidder1", 1), identity()),
                givenImp(singletonMap("bidder2", 2), identity()),
                givenImp(singletonMap("bidder3", 3), identity())),
                bidRequestBuilder -> bidRequestBuilder
                        .user(User.builder().ext(mapper.valueToTree(
                                ExtUser.of(ExtUserPrebid.of(uids), null, null, null, null))).build())
                        .regs(Regs.of(null, mapper.valueToTree(ExtRegs.of(1)))));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

        // then
        final ArgumentCaptor<BidRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpBidderRequester, times(3)).requestBids(any(), bidRequestCaptor.capture(), any(), anyBoolean());
        verify(metrics).updateGdprMaskedMetric(eq("bidder3"));
        assertThat(bidRequestCaptor.getAllValues())
                .extracting(BidRequest::getUser)
                .extracting(User::getBuyeruid).containsExactlyInAnyOrder(null, "uid1", "uid2");
    }

    @Test
    public void shouldPassGdprConsentIfMaskingApplied() {
        // given
        given(bidderCatalog.bidderInfoByName("bidder1")).willReturn(givenBidderInfo(1, true));

        given(gdprService.resultByVendor(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(GdprResponse.of(true, singletonMap(1, false), null)));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder1", 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(User.builder().ext(mapper.valueToTree(
                                ExtUser.of(null, "consent", null, null, null))).build())
                        .regs(Regs.of(null, mapper.valueToTree(ExtRegs.of(1)))));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

        // then
        final ArgumentCaptor<BidRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpBidderRequester).requestBids(any(), bidRequestCaptor.capture(), any(), anyBoolean());

        assertThat(bidRequestCaptor.getAllValues())
                .extracting(BidRequest::getUser)
                .extracting(User::getExt)
                .containsOnly(mapper.valueToTree(ExtUser.of(null, "consent", null, null, null)));
    }

    @Test
    public void shouldPassGdprConsentIfMaskingIsNotApplied() {
        // given
        given(bidderCatalog.bidderInfoByName("bidder1")).willReturn(givenBidderInfo(1, false));

        given(gdprService.resultByVendor(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(GdprResponse.of(true, singletonMap(1, true), null)));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder1", 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(User.builder().ext(mapper.valueToTree(
                                ExtUser.of(null, "consent", null, null, null))).build())
                        .regs(Regs.of(null, mapper.valueToTree(ExtRegs.of(1)))));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

        // then
        final ArgumentCaptor<BidRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpBidderRequester).requestBids(any(), bidRequestCaptor.capture(), any(), anyBoolean());

        assertThat(bidRequestCaptor.getAllValues())
                .extracting(BidRequest::getUser)
                .extracting(User::getExt)
                .containsOnly(mapper.valueToTree(ExtUser.of(null, "consent", null, null, null)));
    }

    @Test
    public void shouldApplyGdprMaskingIfDeviceLmtIsEnabled() {
        // given
        given(bidderCatalog.bidderInfoByName("bidder1")).willReturn(givenBidderInfo(1, true));
        given(gdprService.resultByVendor(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(GdprResponse.of(true, singletonMap(1, true), null)));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder1", 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .device(Device.builder().lmt(1).build())
                        .user(User.builder().buyeruid("to_be_masked").build()));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

        // then
        final ArgumentCaptor<BidRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpBidderRequester).requestBids(any(), bidRequestCaptor.capture(), any(), anyBoolean());

        assertThat(bidRequestCaptor.getAllValues())
                .extracting(BidRequest::getUser)
                .extracting(User::getBuyeruid).hasSize(1)
                .containsNull();
    }

    @Test
    public void shouldNotApplyGdprMaskingIfDeviceLmtIsZero() {
        // given
        given(bidderCatalog.bidderInfoByName("bidder1")).willReturn(givenBidderInfo(1, true));
        given(gdprService.resultByVendor(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(GdprResponse.of(true, singletonMap(1, true), null)));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder1", 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .device(Device.builder().lmt(0).build())
                        .user(User.builder().buyeruid("should_not_be_masked").build()));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

        // then
        final ArgumentCaptor<BidRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpBidderRequester).requestBids(any(), bidRequestCaptor.capture(), any(), anyBoolean());

        assertThat(bidRequestCaptor.getAllValues())
                .extracting(BidRequest::getUser)
                .extracting(User::getBuyeruid)
                .containsOnly("should_not_be_masked");
    }

    @Test
    public void shouldReturnFailedFutureWithPrebidExceptionAsCauseIfGdprServiceFails() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());

        given(gdprService.resultByVendor(any(), any(), any(), any(), any()))
                .willReturn(Future.failedFuture("The gdpr param must be either 0 or 1, given: -1"));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .regs(Regs.of(null, mapper.valueToTree(ExtRegs.of(1)))));

        // when
        final Future<?> result = exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).hasMessage("The gdpr param must be either 0 or 1, given: -1");
    }

    @Test
    public void shouldThrowPrebidExceptionIfExtRegsCannotBeParsed() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .regs(Regs.of(null, mapper.createObjectNode().put("gdpr", "invalid"))));

        // when and then
        assertThatThrownBy(() -> exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessageStartingWith("Error decoding bidRequest.regs.ext:");
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
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

        // then
        final ArgumentCaptor<BidRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpBidderRequester).requestBids(same(bidder), bidRequestCaptor.capture(), any(), anyBoolean());
        assertThat(bidRequestCaptor.getValue().getImp()).hasSize(1)
                .extracting(imp -> imp.getExt().get("bidder").asInt())
                .contains(1);
    }

    @Test
    public void shouldExtractMultipleRequestsForTheSameBidderIfAliasesWasUsed() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(singletonList(
                givenImp(doubleMap("bidder", 1, "bidderAlias", 2), identity())),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("bidderAlias", "bidder")).build()))));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

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
    public void shouldSpecifyNbrInResponseIfNoValidBidders() {
        // given
        final BidRequest bidRequest = givenBidRequest(emptyList());

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        assertThat(bidResponse).returns(2, BidResponse::getNbr);
    }

    @Test
    public void shouldTolerateBidderResultWithoutBids() {
        // given
        givenBidder(givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        assertThat(bidResponse.getSeatbid()).isEmpty();
    }

    @Test
    public void shouldReturnPopulatedSeatBid() {
        // given
        givenBidder(BidderSeatBid.of(
                singletonList(BidderBid.of(
                        Bid.builder().id("bidId").price(BigDecimal.ONE)
                                .ext(mapper.valueToTree(singletonMap("bidExt", 1))).build(), banner, null)),
                emptyList(),
                emptyList()));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1).element(0).isEqualTo(SeatBid.builder()
                .seat("someBidder")
                .group(0)
                .bid(singletonList(Bid.builder()
                        .id("bidId")
                        .price(BigDecimal.ONE)
                        .ext(mapper.valueToTree(
                                ExtPrebid.of(ExtBidPrebid.of(banner, null, null, null), singletonMap("bidExt", 1))))
                        .build()))
                .build());
    }

    @Test
    public void shouldTolerateMissingExtInSeatBidAndBid() {
        // given
        givenBidder(givenSingleSeatBid(BidderBid.of(Bid.builder().id("bidId").price(BigDecimal.ONE).build(),
                banner, null)));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1).element(0).isEqualTo(SeatBid.builder()
                .seat("someBidder")
                .group(0)
                .bid(singletonList(Bid.builder()
                        .id("bidId")
                        .price(BigDecimal.ONE)
                        .ext(mapper.valueToTree(ExtPrebid.of(ExtBidPrebid.of(banner, null, null, null), null)))
                        .build()))
                .build());
    }

    @Test
    public void shouldReturnMultipleSeatBids() {
        // given
        given(httpBidderRequester.requestBids(any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(asList(
                        givenBid(Bid.builder().price(BigDecimal.ONE).build()),
                        givenBid(Bid.builder().price(BigDecimal.ONE).build())))))
                .willReturn(Future.succeededFuture(givenSingleSeatBid(
                        givenBid(Bid.builder().price(BigDecimal.ONE).build()))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(doubleMap("bidder1", 1, "bidder2", 2)));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(2)
                .extracting(seatBid -> seatBid.getBid().size()).containsOnly(2, 1);
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

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        verify(httpBidderRequester, times(2)).requestBids(any(), any(), any(), anyBoolean());
        assertThat(bidResponse.getSeatbid()).hasSize(2)
                .extracting(seatBid -> seatBid.getBid().size()).containsOnly(1, 1);
    }

    @Test
    public void shouldPopulateBidResponseExtension() throws JsonProcessingException {
        // given
        givenBidder("bidder1", mock(Bidder.class), BidderSeatBid.of(
                singletonList(givenBid(Bid.builder().price(BigDecimal.ONE).build())), emptyList(),
                singletonList(BidderError.badServerResponse("bidder1_error1"))));
        givenBidder("bidder2", mock(Bidder.class), BidderSeatBid.of(
                singletonList(givenBid(Bid.builder().price(BigDecimal.ONE).build())), emptyList(),
                asList(BidderError.badServerResponse("bidder2_error1"),
                        BidderError.badServerResponse("bidder2_error2"))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(doubleMap("bidder1", 1, "bidder2", 2)));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        final ExtBidResponse ext = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);
        assertThat(ext.getResponsetimemillis()).hasSize(2).containsOnlyKeys("bidder1", "bidder2");
        assertThat(ext.getErrors()).hasSize(2).containsOnly(
                entry("bidder1", singletonList(ExtBidderError.of(BidderError.Type.bad_server_response.getCode(),
                        "bidder1_error1"))),
                entry("bidder2", asList(ExtBidderError.of(BidderError.Type.bad_server_response.getCode(),
                        "bidder2_error1"), ExtBidderError.of(BidderError.Type.bad_server_response.getCode(),
                        "bidder2_error2"))));
    }

    @Test
    public void shouldPopulateBidResponseDebugExtensionIfTestFlagIsTrue() throws JsonProcessingException {
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
        givenBidder("bidder2", mock(Bidder.class), BidderSeatBid.of(
                singletonList(givenBid(Bid.builder().price(BigDecimal.ONE).build())),
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
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

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
    public void shouldPopulateBidResponseDebugExtensionIfExtPrebidDebugIsTrue() throws JsonProcessingException {
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
        givenBidder("bidder2", mock(Bidder.class), BidderSeatBid.of(
                singletonList(givenBid(Bid.builder().price(BigDecimal.ONE).build())),
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
                builder -> builder.ext(
                        mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder().debug(true).build()))));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

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
        givenBidder(BidderSeatBid.of(
                singletonList(givenBid(Bid.builder().price(BigDecimal.ONE).build())),
                singletonList(ExtHttpCall.builder()
                        .uri("bidder1_uri1")
                        .requestbody("bidder1_requestBody1")
                        .status(200)
                        .responsebody("bidder1_responseBody1")
                        .build()),
                emptyList()));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder1", 1)));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

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
        final Future<BidResponse> result =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

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
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

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
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

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
                        .targeting(ExtRequestTargeting.of(
                                Json.mapper.valueToTree(ExtPriceGranularity.of(2, singletonList(
                                        ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.5))))),
                                null, null, true, true))
                        .build()))));

        given(responseBidValidator.validate(any()))
                .willReturn(ValidationResult.error("bid validation error"));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        final ExtBidResponse ext = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);
        assertThat(ext.getErrors()).hasSize(1).containsOnly(
                entry("bidder1", singletonList(ExtBidderError.of(BidderError.Type.generic.getCode(),
                        "bid validation error"))));
    }

    @Test
    public void shouldPopulateTargetingKeywords() {
        // given
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(asList(
                givenBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build()),
                givenBid(Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.valueOf(6.35)).build()))));
        givenBidder("bidder2", mock(Bidder.class), givenSeatBid(asList(
                givenBid(Bid.builder().id("bidId3").impid("impId1").price(BigDecimal.valueOf(7.19)).build()),
                givenBid(Bid.builder().id("bidId4").impid("impId2").price(BigDecimal.valueOf(4.99)).build()))));

        final BidRequest bidRequest = givenBidRequest(asList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(doubleMap("bidder1", 1, "bidder2", 2), builder -> builder.id("impId1")),
                givenImp(doubleMap("bidder1", 1, "bidder2", 2), builder -> builder.id("impId2"))),
                builder -> builder.ext(
                        mapper.valueToTree(
                                ExtBidRequest.of(ExtRequestPrebid.builder()
                                        .targeting(ExtRequestTargeting.of(
                                                Json.mapper.valueToTree(ExtPriceGranularity.of(2, singletonList(
                                                        ExtGranularityRange.of(BigDecimal.valueOf(5),
                                                                BigDecimal.valueOf(0.5))))), null, null, true, true))
                                        .build()))));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(
                        Bid::getId,
                        bid -> toExtPrebid(bid.getExt()).getPrebid().getTargeting().get("hb_pb"))
                .containsOnly(
                        tuple("bidId1", null),
                        tuple("bidId2", "5.00"),
                        tuple("bidId3", "5.00"),
                        tuple("bidId4", null));
    }

    @Test
    public void shouldPopulateTargetingKeywordsFromMediaTypePriceGranularities() {
        // given
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(asList(
                givenBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build()),
                givenBid(Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.valueOf(6.35)).build()))));
        givenBidder("bidder2", mock(Bidder.class), givenSeatBid(asList(
                givenBid(Bid.builder().id("bidId3").impid("impId1").price(BigDecimal.valueOf(7.19)).build()),
                givenBid(Bid.builder().id("bidId4").impid("impId2").price(BigDecimal.valueOf(4.99)).build()))));

        final BidRequest bidRequest = givenBidRequest(asList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(doubleMap("bidder1", 1, "bidder2", 2), builder -> builder.id("impId1")),
                givenImp(doubleMap("bidder1", 1, "bidder2", 2), builder -> builder.id("impId2"))),
                builder -> builder.ext(
                        mapper.valueToTree(
                                ExtBidRequest.of(ExtRequestPrebid.builder()
                                        .targeting(ExtRequestTargeting.of(
                                                Json.mapper.valueToTree(ExtPriceGranularity.of(2, singletonList(
                                                        ExtGranularityRange.of(BigDecimal.valueOf(5),
                                                                BigDecimal.valueOf(0.5))))),
                                                ExtMediaTypePriceGranularity.of(Json.mapper.valueToTree(
                                                        ExtPriceGranularity.of(3, singletonList(
                                                                ExtGranularityRange.of(BigDecimal.valueOf(10),
                                                                        BigDecimal.valueOf(1))))), null, null), null,
                                                true, true))
                                        .build())
                        )));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(
                        Bid::getId,
                        bid -> toExtPrebid(bid.getExt()).getPrebid().getTargeting().get("hb_pb"))
                .containsOnly(
                        tuple("bidId1", null),
                        tuple("bidId2", "6.000"),
                        tuple("bidId3", "7.000"),
                        tuple("bidId4", null));
    }

    @Test
    public void shouldPopulateCacheIdTargetingKeywords() {
        // given
        final Bid bid1 = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(singletonList(givenBid(bid1))));

        final Bid bid2 = Bid.builder().id("bidId2").impid("impId1").price(BigDecimal.valueOf(7.19)).build();
        givenBidder("bidder2", mock(Bidder.class), givenSeatBid(singletonList(givenBid(bid2))));

        given(cacheService.cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any()))
                .willReturn(Future.succeededFuture(singletonMap(bid2, CacheIdInfo.of("cacheId2", null))));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(doubleMap("bidder1", 1, "bidder2", 2), builder -> builder.id("impId1"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.of(Json.mapper.valueToTree(ExtPriceGranularity.of(
                                2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                                        BigDecimal.valueOf(0.5))))), null, null, true, true))
                        .cache(ExtRequestPrebidCache.of(ExtRequestPrebidCacheBids.of(null, null), null))
                        .build()))));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

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
    public void shouldReturnCreativeForBannerAndNotReturnForVideo() {
        // given
        final Bid bid1 = Bid.builder().id("bidId1").adm("adm1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(singletonList(givenBid(bid1))));

        final Bid bid2 = Bid.builder().id("bidId2").adm("adm2").impid("impId2").price(BigDecimal.valueOf(7.19)).build();
        givenBidder("bidder2", mock(Bidder.class), givenSeatBid(singletonList(givenBid(bid2))));

        given(cacheService.cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any()))
                .willReturn(Future.succeededFuture(singletonMap(bid1, CacheIdInfo.of(null, "cacheId1"))));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(doubleMap("bidder1", 1, "bidder2", 2),
                        builder -> builder.id("impId1").video(Video.builder().build()))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.of(Json.mapper.valueToTree(ExtPriceGranularity.of(
                                2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                                        BigDecimal.valueOf(0.5))))), null, null, true, true))
                        .cache(ExtRequestPrebidCache.of(ExtRequestPrebidCacheBids.of(null, true),
                                ExtRequestPrebidCacheVastxml.of(null, false)))
                        .build()))));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(Bid::getAdm)
                .containsOnly("adm2", null);
    }

    @Test
    public void shouldPopulateUuidTargetingKeywords() {
        // given
        final Bid bid1 = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(singletonList(givenBid(bid1))));

        final Bid bid2 = Bid.builder().id("bidId2").impid("impId1").price(BigDecimal.valueOf(7.19)).build();
        givenBidder("bidder2", mock(Bidder.class), givenSeatBid(singletonList(givenBid(bid2))));

        given(cacheService.cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any()))
                .willReturn(Future.succeededFuture(singletonMap(bid2, CacheIdInfo.of(null, "videoCacheId2"))));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(doubleMap("bidder1", 1, "bidder2", 2),
                        builder -> builder.id("impId1").video(Video.builder().build()))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.of(Json.mapper.valueToTree(ExtPriceGranularity.of(
                                2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                                        BigDecimal.valueOf(0.5))))), null, null, true, true))
                        .cache(ExtRequestPrebidCache.of(null, ExtRequestPrebidCacheVastxml.of(null, null)))
                        .build()))));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(bid -> toExtPrebid(bid.getExt()).getPrebid().getTargeting())
                .extracting(
                        targeting -> targeting.get("hb_bidder"),
                        targeting -> targeting.get("hb_uuid_bidder2"),
                        targeting -> targeting.get("hb_uuid"))
                .containsOnly(
                        tuple(null, null, null),
                        tuple("bidder2", "videoCacheId2", "videoCacheId2"));
    }

    @Test
    public void shouldPopulateCacheHostAndCachePathTargetingKeywords() {
        // given
        final Bid bid1 = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(singletonList(givenBid(bid1))));

        final Bid bid2 = Bid.builder().id("bidId2").impid("impId1").price(BigDecimal.valueOf(7.19)).build();
        givenBidder("bidder2", mock(Bidder.class), givenSeatBid(singletonList(givenBid(bid2))));

        given(cacheService.cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any()))
                .willReturn(Future.succeededFuture(singletonMap(bid2, CacheIdInfo.of(null, "videoCacheId2"))));

        given(cacheService.getEndpointHost()).willReturn("someHost");
        given(cacheService.getEndpointPath()).willReturn("somePath");

        final BidRequest bidRequest = givenBidRequest(singletonList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(doubleMap("bidder1", 1, "bidder2", 2),
                        builder -> builder.id("impId1").video(Video.builder().build()))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.of(Json.mapper.valueToTree(ExtPriceGranularity.of(
                                2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                                        BigDecimal.valueOf(0.5))))), null, null, true, true))
                        .cache(ExtRequestPrebidCache.of(null, ExtRequestPrebidCacheVastxml.of(null, null)))
                        .build()))));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(bid -> toExtPrebid(bid.getExt()).getPrebid().getTargeting())
                .extracting(targeting -> targeting.get("hb_cache_host"),
                        targeting -> targeting.get("hb_cache_path"))
                .containsOnly(tuple("someHost", "somePath"),
                        tuple(null, null));
    }

    @Test
    public void shouldNotPopulateCacheIdTargetingKeywordsIfCacheServiceReturnEmptyResult() {
        // given
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build()))));
        givenBidder("bidder2", mock(Bidder.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().id("bidId2").impid("impId1").price(BigDecimal.valueOf(7.19)).build()))));

        given(cacheService.cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any()))
                .willReturn(Future.succeededFuture(emptyMap()));


        final BidRequest bidRequest = givenBidRequest(singletonList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(doubleMap("bidder1", 1, "bidder2", 2), builder -> builder.id("impId1"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.of(Json.mapper.valueToTree(ExtPriceGranularity.of(2,
                                singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.5))))),
                                null, null, true, true))
                        .cache(ExtRequestPrebidCache.of(ExtRequestPrebidCacheBids.of(null, null), null))
                        .build()))));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

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
        final Bid bid1 = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.ZERO).build();
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(singletonList(givenBid(bid1))));

        final Bid bid2 = Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.valueOf(5.67)).build();
        givenBidder("bidder2", mock(Bidder.class), givenSeatBid(singletonList(givenBid(bid2))));

        given(cacheService.cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any()))
                .willReturn(Future.succeededFuture(singletonMap(bid2, CacheIdInfo.of("cacheId2", null))));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(doubleMap("bidder1", 1, "bidder2", 2), builder -> builder.id("impId2"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.of(Json.mapper.valueToTree(ExtPriceGranularity.of(2,
                                singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.5))))),
                                null, null, true, true))
                        .cache(ExtRequestPrebidCache.of(ExtRequestPrebidCacheBids.of(null, null), null))
                        .build()))));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

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
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(asList(
                givenBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(1.23)).build()),
                givenBid(Bid.builder().id("bidId2").impid("impId1").price(BigDecimal.valueOf(4.56)).build()))));
        givenBidder("bidder2", mock(Bidder.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().id("bidId3").impid("impId1").price(BigDecimal.valueOf(7.89)).build()))));

        final BidRequest bidRequest = givenBidRequest(asList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1")),
                givenImp(doubleMap("bidder1", 1, "bidder2", 2), builder -> builder.id("impId1"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.of(Json.mapper.valueToTree(ExtPriceGranularity.of(2,
                                singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.5))))),
                                null, null, true, true))
                        .build()))));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

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
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(1.23)).build()))));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.of(Json.mapper.valueToTree(ExtPriceGranularity.of(2,
                                singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.5))))),
                                null, null, false, true))
                        .build()))));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

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
    public void shouldNotPopulateBidderKeysTargetingIfIncludeBidderKeysFlagIsFalse() {
        // given
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(singletonList(
                givenBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(1.23)).build()))));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.of(Json.mapper.valueToTree(ExtPriceGranularity.of(2,
                                singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.5))))),
                                null, null, true, false))
                        .build()))));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(
                        Bid::getId,
                        bid -> toTargetingByKey(bid, "hb_bidder"),
                        bid -> toTargetingByKey(bid, "hb_bidder_bidder1"))
                .containsOnly(
                        tuple("bidId1", "bidder1", null));
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
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

        // then
        final BidRequest capturedBidRequest = captureBidRequest();
        assertThat(capturedBidRequest.getUser()).isSameAs(user);
    }

    @Test
    public void shouldHonorBuyeridFromRequestAndClearBuyerIdsFromUserExtPrebidIfContains() {
        // given
        givenBidder(givenEmptySeatBid());

        given(uidsCookie.uidFrom(anyString())).willReturn("buyeridFromCookie");
        final Map<String, String> uids = new HashMap<>();
        uids.put("someBidder", "uidval");

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.user(User.builder().buyeruid("buyeridFromRequest").ext(mapper.valueToTree(
                        ExtUser.of(ExtUserPrebid.of(uids), null, null, null, null))).build()));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

        // then
        final User capturedBidRequestUser = captureBidRequest().getUser();
        assertThat(capturedBidRequestUser).isEqualTo(User.builder().buyeruid("buyeridFromRequest")
                .ext(mapper.valueToTree(ExtUser.of(null, null, null, null, null))).build());
    }

    @Test
    public void shouldSetUserBuyerIdsFromUserExtPrebidAndClearPrebidBuyerIdsAfterwards() {
        // given
        givenBidder(givenEmptySeatBid());
        final Map<String, String> uids = new HashMap<>();
        uids.put("someBidder", "uidval");

        given(uidsCookie.uidFrom(anyString())).willReturn("buyeridFromCookie");

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.user(User.builder().ext(mapper.valueToTree(
                        ExtUser.of(ExtUserPrebid.of(uids), null, null, null, null))).build()));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

        // then
        final User capturedBidRequestUser = captureBidRequest().getUser();
        assertThat(capturedBidRequestUser).isEqualTo(User.builder().buyeruid("uidval")
                .ext(mapper.valueToTree(ExtUser.of(null, null, null, null, null))).build());
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
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

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
                        .user(User.builder().ext(mapper.valueToTree(ExtUser.of(
                                null, null, null, null, dataNode))).build()));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

        // then
        final ArgumentCaptor<BidRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpBidderRequester, times(2)).requestBids(any(), bidRequestCaptor.capture(), any(), anyBoolean());
        final List<BidRequest> capturedBidRequests = bidRequestCaptor.getAllValues();

        assertThat(capturedBidRequests)
                .extracting(BidRequest::getUser)
                .containsOnly(
                        User.builder().ext(mapper.valueToTree(
                                ExtUser.of(null, null, null, null, dataNode))).build(),
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
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

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
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

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
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

        // then
        final BidRequest capturedBidRequest = captureBidRequest();
        assertThat(capturedBidRequest.getUser()).isEqualTo(User.builder().id("userId").buyeruid("buyerid").build());
    }

    @Test
    public void shouldCreateUserIfMissingInRequestAndBuyeridPresentInCookie() {
        // given
        givenBidder(givenEmptySeatBid());

        given(uidsCookie.uidFrom(eq("cookieFamily"))).willReturn("buyerid");

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

        // then
        final BidRequest capturedBidRequest = captureBidRequest();
        assertThat(capturedBidRequest.getUser()).isEqualTo(User.builder().buyeruid("buyerid").build());
    }

    @Test
    public void shouldPassGlobalTimeoutToConnectorUnchangedIfCachingIsNotRequested() {
        // given
        givenBidder(givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

        // then
        verify(httpBidderRequester).requestBids(any(), any(), same(timeout), anyBoolean());
    }

    @Test
    public void shouldPassReducedGlobalTimeoutToConnectorAndOriginalToCacheServiceIfCachingIsRequested() {
        // given
        exchangeService = new ExchangeService(bidderCatalog, httpBidderRequester, responseBidValidator, cacheService,
                bidResponsePostProcessor, currencyService, gdprService, eventsService, metrics, clock, false, 100);

        final Bid bid = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        givenBidder(givenSeatBid(singletonList(givenBid(bid))));

        given(cacheService.cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any()))
                .willReturn(Future.succeededFuture(singletonMap(bid, CacheIdInfo.of(null, null))));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.of(Json.mapper.valueToTree(ExtPriceGranularity.of(2,
                                singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.5))))),
                                null, null, true, true))
                        .cache(ExtRequestPrebidCache.of(ExtRequestPrebidCacheBids.of(null, null), null))
                        .build()))));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

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
                builder -> builder.ext(
                        mapper.valueToTree(
                                ExtBidRequest.of(ExtRequestPrebid.builder()
                                        .targeting(ExtRequestTargeting.of(
                                                Json.mapper.valueToTree(ExtPriceGranularity.of(2, singletonList(
                                                        ExtGranularityRange.of(BigDecimal.valueOf(5),
                                                                BigDecimal.valueOf(0.5))))), null, null, true, true))
                                        .cache(ExtRequestPrebidCache.of(ExtRequestPrebidCacheBids.of(null, null), null))
                                        .build()))));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

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
                builder -> builder.ext(
                        mapper.valueToTree(
                                ExtBidRequest.of(ExtRequestPrebid.builder()
                                        .targeting(ExtRequestTargeting.of(
                                                Json.mapper.valueToTree(ExtPriceGranularity.of(2, singletonList(
                                                        ExtGranularityRange.of(BigDecimal.valueOf(5),
                                                                BigDecimal.valueOf(0.5))))), null, null, true, true))
                                        .cache(ExtRequestPrebidCache.of(ExtRequestPrebidCacheBids.of(null, null), null))
                                        .build()))));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

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

        given(currencyService.convertCurrency(any(), any(), any(), any())).willReturn(BigDecimal.valueOf(5.0));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getPrice).containsExactly(BigDecimal.valueOf(5.0));
    }

    @Test
    public void shouldReturnSameBidPriceIfNoChangesAppliedToBidPrice() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenSeatBid(singletonList(
                givenBid(Bid.builder().price(BigDecimal.valueOf(2.0)).build()))));

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                identity());

        // returns the same price as in argument
        given(currencyService.convertCurrency(any(), any(), any(), any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getPrice).containsExactly(BigDecimal.valueOf(2.0));
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

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid).isEmpty();
        final ExtBidResponse ext = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);
        assertThat(ext.getErrors()).hasSize(1).containsOnly(
                entry("bidder", singletonList(ExtBidderError.of(BidderError.Type.generic.getCode(),
                        "no currency conversion available"))));
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

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getPrice).containsExactly(BigDecimal.valueOf(100));
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

        given(currencyService.convertCurrency(any(), any(), any(), any())).willReturn(BigDecimal.valueOf(10.0))
                .willThrow(new PreBidException("no currency conversion available"));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getPrice).containsExactly(BigDecimal.valueOf(10.0));
        final ExtBidResponse ext = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);
        assertThat(ext.getErrors()).hasSize(1).containsOnly(
                entry("bidder", singletonList(ExtBidderError.of(BidderError.Type.generic.getCode(),
                        "no currency conversion available"))));
    }

    @Test
    public void shouldRespondWithErrorWhenBidsWithUnsupportedCurrency()
            throws JsonProcessingException {
        // given
        final Bidder<?> bidderRequester = mock(Bidder.class);
        givenBidder("bidder", bidderRequester, givenSeatBid(singletonList(
                givenBid(Bid.builder().price(BigDecimal.valueOf(2.0)).build()))));

        final BidRequest bidRequest = BidRequest.builder().cur(Collections.singletonList("EUR"))
                .imp(singletonList(givenImp(singletonMap("bidder", 2), identity()))).build();

        // returns the same price as in argument
        given(currencyService.convertCurrency(any(), any(), any(), any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(0);
        final ExtBidResponse ext = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);
        assertThat(ext.getErrors()).hasSize(1).containsOnly(entry("bidder",
                singletonList(ExtBidderError.of(BidderError.Type.generic.getCode(),
                        "Bid currency is not allowed. Was EUR, wants: [USD]"))));
    }

    @Test
    public void shouldRespondWithErrorWhenBidsWithDifferentCurrencies() throws JsonProcessingException {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(true);

        given(httpBidderRequester.requestBids(any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(asList(
                        BidderBid.of(Bid.builder().price(TEN).build(), BidType.banner, "EUR"),
                        BidderBid.of(Bid.builder().price(TEN).build(), BidType.banner, "USD")))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("somebidder", 1)),
                builder -> builder.site(Site.builder().build()));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();


        // then
        assertThat(bidResponse.getSeatbid()).hasSize(0);
        final ExtBidResponse ext = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);
        assertThat(ext.getErrors()).hasSize(1).containsOnly(entry("somebidder",
                singletonList(ExtBidderError.of(BidderError.Type.generic.getCode(),
                        "Bid currencies mismatch found. Expected all bids to have the same currencies."))));
    }

    @Test
    public void shouldAddExtPrebidEventsFromSitePublisher() {
        // given
        given(eventsService.isEventsEnabled(anyString(), any())).willReturn(Future.succeededFuture(true));
        given(eventsService.createEvent(anyString(), anyString()))
                .willReturn(Events.of(
                        "http://external.org/event?type=win&bidid=bidId&bidder=someBidder",
                        "http://external.org/event?type=view&bidid=bidId&bidder=someBidder"));

        givenBidder(BidderSeatBid.of(
                singletonList(BidderBid.of(
                        Bid.builder().id("bidId").price(BigDecimal.ONE)
                                .ext(mapper.valueToTree(singletonMap("bidExt", 1))).build(), banner, null)),
                emptyList(),
                emptyList()));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder.site(Site.builder()
                        .publisher(Publisher.builder().id("1001").build()).build()));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1).element(0).isEqualTo(SeatBid.builder()
                .seat("someBidder")
                .group(0)
                .bid(singletonList(Bid.builder()
                        .id("bidId")
                        .price(BigDecimal.ONE)
                        .ext(mapper.valueToTree(
                                ExtPrebid.of(ExtBidPrebid.of(banner, null, null, Events.of(
                                        "http://external.org/event?type=win&bidid=bidId&bidder=someBidder",
                                        "http://external.org/event?type=view&bidid=bidId&bidder=someBidder")),
                                        singletonMap("bidExt", 1))))
                        .build()))
                .build());
    }

    @Test
    public void shouldAddExtPrebidEventsFromAppPublisher() {
        // given
        given(eventsService.isEventsEnabled(anyString(), any())).willReturn(Future.succeededFuture(true));
        given(eventsService.createEvent(anyString(), anyString()))
                .willReturn(Events.of(
                        "http://external.org/event?type=win&bidid=bidId&bidder=someBidder",
                        "http://external.org/event?type=view&bidid=bidId&bidder=someBidder"));

        givenBidder(BidderSeatBid.of(
                singletonList(BidderBid.of(
                        Bid.builder().id("bidId").price(BigDecimal.ONE)
                                .ext(mapper.valueToTree(singletonMap("bidExt", 1))).build(), banner, null)),
                emptyList(),
                emptyList()));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder.app(App.builder()
                        .publisher(Publisher.builder().id("1001").build()).build()));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1).element(0).isEqualTo(SeatBid.builder()
                .seat("someBidder")
                .group(0)
                .bid(singletonList(Bid.builder()
                        .id("bidId")
                        .price(BigDecimal.ONE)
                        .ext(mapper.valueToTree(
                                ExtPrebid.of(ExtBidPrebid.of(banner, null, null, Events.of(
                                        "http://external.org/event?type=win&bidid=bidId&bidder=someBidder",
                                        "http://external.org/event?type=view&bidid=bidId&bidder=someBidder")),
                                        singletonMap("bidExt", 1))))
                        .build()))
                .build());
    }

    @Test
    public void shouldNotAddExtPrebidEventsWhenEventsServiceReturnsEmptyEventsService() {
        givenBidder(BidderSeatBid.of(
                singletonList(BidderBid.of(
                        Bid.builder().id("bidId").price(BigDecimal.ONE)
                                .ext(mapper.valueToTree(singletonMap("bidExt", 1))).build(), banner, null)),
                emptyList(),
                emptyList()));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder.app(App.builder()
                        .publisher(Publisher.builder().id("1001").build()).build()));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1).element(0).isEqualTo(SeatBid.builder()
                .seat("someBidder")
                .group(0)
                .bid(singletonList(Bid.builder()
                        .id("bidId")
                        .price(BigDecimal.ONE)
                        .ext(mapper.valueToTree(
                                ExtPrebid.of(ExtBidPrebid.of(banner, null, null, null),
                                        singletonMap("bidExt", 1))))
                        .build()))
                .build());
    }

    @Test
    public void shouldIncrementCommonMetrics() {
        // given
        given(httpBidderRequester.requestBids(any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(singletonList(
                        givenBid(Bid.builder().price(TEN).build())))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("somebidder", 1)),
                builder -> builder.site(Site.builder().publisher(Publisher.builder().id("accountId").build()).build()));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

        // then
        verify(metrics).updateAccountRequestMetrics(eq("accountId"), eq(MetricName.openrtb2web));
        verify(metrics)
                .updateAdapterRequestTypeAndNoCookieMetrics(eq("somebidder"), eq(MetricName.openrtb2web), eq(true));
        verify(metrics).updateAdapterResponseTime(eq("somebidder"), eq("accountId"), anyInt());
        verify(metrics).updateAdapterRequestGotbidsMetrics(eq("somebidder"), eq("accountId"));
        verify(metrics).updateAdapterBidMetrics(eq("somebidder"), eq("accountId"), eq(10000L), eq(false), eq("banner"));
    }

    @Test
    public void shouldCallUpdateCookieMetricsWithExpectedValue() {
        // given
        given(bidderCatalog.isActive(any())).willReturn(false);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("somebidder", 1)),
                builder -> builder.app(App.builder().build()));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

        // then
        verify(metrics).updateAdapterRequestTypeAndNoCookieMetrics(
                eq("somebidder"), eq(MetricName.openrtb2web), eq(false));
    }

    @Test
    public void shouldUseEmptyStringIfPublisherIdIsNull() {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(true);
        given(httpBidderRequester.requestBids(any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(singletonList(
                        givenBid(Bid.builder().price(TEN).build())))));
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("somebidder", 1)),
                builder -> builder.site(Site.builder().publisher(Publisher.builder().build()).build()));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

        // then
        verify(metrics).updateAccountRequestMetrics(eq(""), eq(MetricName.openrtb2web));
    }

    @Test
    public void shouldIncrementNoBidRequestsMetric() {
        // given
        given(httpBidderRequester.requestBids(any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(emptyList())));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("somebidder", 1)),
                builder -> builder.app(App.builder().publisher(Publisher.builder().id("accountId").build()).build()));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

        // then
        verify(metrics).updateAdapterRequestNobidMetrics(eq("somebidder"), eq("accountId"));
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

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("somebidder", 1)),
                builder -> builder.site(Site.builder().publisher(Publisher.builder().id("accountId").build()).build()));

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

        // then
        verify(metrics).updateAdapterRequestGotbidsMetrics(eq("somebidder"), eq("accountId"));
        verify(metrics).updateAdapterRequestErrorMetric(eq("somebidder"), eq(MetricName.badinput));
        verify(metrics).updateAdapterRequestErrorMetric(eq("somebidder"), eq(MetricName.badserverresponse));
        verify(metrics).updateAdapterRequestErrorMetric(eq("somebidder"), eq(MetricName.failedtorequestbids));
        verify(metrics).updateAdapterRequestErrorMetric(eq("somebidder"), eq(MetricName.timeout));
        verify(metrics).updateAdapterRequestErrorMetric(eq("somebidder"), eq(MetricName.unknown_error));
    }

    @Test
    public void shouldPassResponseToPostProcessor() {
        // given
        final BidRequest bidRequest = givenBidRequest(emptyList());

        // when
        exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null);

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

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getPrice).containsExactly(BigDecimal.valueOf(4.936));
    }

    @Test
    public void shouldReturnBidsWithoutAdjustingPricesWhenAdjustmentFactorNotPresentForBidder() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenSeatBid(singletonList(
                givenBid(Bid.builder().price(BigDecimal.valueOf(2.0)).build()))));

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .aliases(emptyMap())
                        .bidadjustmentfactors(singletonMap("some-other-bidder", BigDecimal.TEN))
                        .build()))));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getPrice).containsExactly(BigDecimal.valueOf(2.0));
    }

    @Test
    public void shouldReturnCacheEntityInExt() {
        // given
        final Bid bid = Bid.builder().id("bidId").impid("impId").price(BigDecimal.ONE).build();
        givenBidder("bidder", mock(Bidder.class), givenSeatBid(singletonList(givenBid(bid))));

        given(cacheService.cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any()))
                .willReturn(Future.succeededFuture(singletonMap(bid, CacheIdInfo.of("cacheId", null))));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder", 1), builder -> builder.id("impId"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.of(Json.mapper.valueToTree(ExtPriceGranularity.of(
                                2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                                        BigDecimal.valueOf(0.5))))), null, null, true, true))
                        .cache(ExtRequestPrebidCache.of(ExtRequestPrebidCacheBids.of(null, null), null))
                        .build()))));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(extractedBid -> toExtPrebid(extractedBid.getExt()).getPrebid().getCache())
                .extracting(ExtResponseCache::getBids, ExtResponseCache::getVastXml)
                .containsExactly(tuple(CacheAsset.of(null, "cacheId"), null));
    }

    @Test
    public void shouldReturnCacheError() throws JsonProcessingException {

        // given
        final Bid bid = Bid.builder().id("bidId").impid("impId").price(BigDecimal.ONE).build();
        givenBidder("bidder", mock(Bidder.class), givenSeatBid(singletonList(givenBid(bid))));

        given(cacheService.cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any()))
                .willReturn(Future.failedFuture(new RuntimeException("error")));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder", 1), builder -> builder.id("impId"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.of(Json.mapper.valueToTree(ExtPriceGranularity.of(
                                2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                                        BigDecimal.valueOf(0.5))))), null, null, true, true))
                        .cache(ExtRequestPrebidCache.of(ExtRequestPrebidCacheBids.of(null, null), null))
                        .build()))));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        final ExtBidResponse ext = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);
        assertThat(ext.getErrors()).contains(entry("prebid",
                singletonList(ExtBidderError.of(BidderError.Type.generic.getCode(),
                        "Error occurred while trying to cache bids. Message : error"))));
    }

    @Test
    public void shouldNotContainErrorsIfBidderErrorsAreEmpty() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder().id("bidId").impid("impId").price(BigDecimal.ONE).build();
        givenBidder("bidder", mock(Bidder.class), givenSeatBid(singletonList(givenBid(bid))));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder", 1), builder -> builder.id("impId"))));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        final ExtBidResponse ext = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);
        assertThat(ext.getErrors()).isNull();
    }

    @Test
    public void shouldContainBidRequestTmax() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder().id("bidId").impid("impId").price(BigDecimal.ONE).build();
        givenBidder("bidder", mock(Bidder.class), givenSeatBid(singletonList(givenBid(bid))));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder", 1), builder -> builder.id("impId"))),
                builder -> builder.tmax(5000L));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        final ExtBidResponse ext = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);
        assertThat(ext.getTmaxrequest()).isEqualTo(5000L);
    }

    @Test
    public void shouldContainCacheResponseTime() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder().id("bidId").impid("impId").price(BigDecimal.ONE).build();
        givenBidder("bidder", mock(Bidder.class), givenSeatBid(singletonList(givenBid(bid))));

        given(cacheService.cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any()))
                .willReturn(Future.succeededFuture(singletonMap(bid, CacheIdInfo.of("cacheId", null))));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder", 1), builder -> builder.id("impId"))),
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.of(Json.mapper.valueToTree(ExtPriceGranularity.of(
                                2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                                        BigDecimal.valueOf(0.5))))), null, null, true, true))
                        .cache(ExtRequestPrebidCache.of(ExtRequestPrebidCacheBids.of(null, null), null))
                        .build()))));

        // when
        final BidResponse bidResponse =
                exchangeService.holdAuction(bidRequest, uidsCookie, timeout, metricsContext, null).result();

        // then
        final ExtBidResponse ext = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);
        assertThat(ext.getResponsetimemillis()).containsKeys("cache");
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
        given(httpBidderRequester.requestBids(any(), any(), any(), anyBoolean())).willReturn(
                Future.succeededFuture(response));
    }

    private void givenBidder(String bidderName, Bidder<?> bidder, BidderSeatBid response) {
        given(bidderCatalog.isValidName(eq(bidderName))).willReturn(true);
        doReturn(bidder).when(bidderCatalog).bidderByName(eq(bidderName));
        given(httpBidderRequester.requestBids(same(bidder), any(), any(), anyBoolean())).willReturn(
                Future.succeededFuture(response));
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
}
