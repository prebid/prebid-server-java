package org.prebid.server.auction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BidRequest.BidRequestBuilder;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Dooh;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Imp.ImpBuilder;
import com.iab.openrtb.request.Pmp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.SupplyChain;
import com.iab.openrtb.request.SupplyChainNode;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
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
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.auction.adjustment.BidAdjustmentFactorResolver;
import org.prebid.server.auction.mediatypeprocessor.MediaTypeProcessingResult;
import org.prebid.server.auction.mediatypeprocessor.MediaTypeProcessor;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidRequestCacheInfo;
import org.prebid.server.auction.model.BidderPrivacyResult;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.auction.model.MultiBidConfig;
import org.prebid.server.auction.model.StoredResponseResult;
import org.prebid.server.auction.model.TimeoutContext;
import org.prebid.server.auction.model.debug.DebugContext;
import org.prebid.server.auction.privacy.enforcement.PrivacyEnforcementService;
import org.prebid.server.auction.versionconverter.BidRequestOrtbVersionConversionManager;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderInfo;
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
import org.prebid.server.floors.PriceFloorAdjuster;
import org.prebid.server.floors.PriceFloorEnforcer;
import org.prebid.server.hooks.execution.HookStageExecutor;
import org.prebid.server.hooks.execution.model.ExecutionAction;
import org.prebid.server.hooks.execution.model.ExecutionStatus;
import org.prebid.server.hooks.execution.model.GroupExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionOutcome;
import org.prebid.server.hooks.execution.v1.auction.AuctionResponsePayloadImpl;
import org.prebid.server.hooks.execution.v1.bidder.BidderRequestPayloadImpl;
import org.prebid.server.hooks.execution.v1.bidder.BidderResponsePayloadImpl;
import org.prebid.server.hooks.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.v1.analytics.AppliedToImpl;
import org.prebid.server.hooks.v1.analytics.ResultImpl;
import org.prebid.server.hooks.v1.analytics.TagsImpl;
import org.prebid.server.log.CriteriaLogManager;
import org.prebid.server.log.HttpInteractionLogger;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.Endpoint;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.model.UpdateResult;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtAppPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtBidderConfig;
import org.prebid.server.proto.openrtb.ext.request.ExtBidderConfigOrtb;
import org.prebid.server.proto.openrtb.ext.request.ExtDooh;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtImpAuctionEnvironment;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentFactors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestCurrency;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidBidderConfig;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheBids;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheVastxml;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidDataEidPermissions;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidMultiBid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchain;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserPrebid;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidderError;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.proto.openrtb.ext.response.ExtModules;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTrace;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceAnalyticsActivity;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceAnalyticsAppliedTo;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceAnalyticsResult;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceAnalyticsTags;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceGroup;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceInvocationResult;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceStage;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceStageOutcome;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseDebug;
import org.prebid.server.proto.openrtb.ext.response.FledgeAuctionConfig;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountEventsConfig;
import org.prebid.server.spring.config.bidder.model.CompressionType;
import org.prebid.server.spring.config.bidder.model.Ortb;
import org.prebid.server.validation.ResponseBidValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.UnaryOperator.identity;
import static org.apache.commons.lang3.exception.ExceptionUtils.rethrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.prebid.server.proto.openrtb.ext.response.BidType.audio;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class ExchangeServiceTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderCatalog bidderCatalog;

    @Mock
    private StoredResponseProcessor storedResponseProcessor;

    @Mock
    private PrivacyEnforcementService privacyEnforcementService;

    @Mock
    private FpdResolver fpdResolver;

    @Mock
    private SupplyChainResolver supplyChainResolver;

    @Mock
    private DebugResolver debugResolver;

    @Mock
    private MediaTypeProcessor mediaTypeProcessor;

    @Mock
    private UidUpdater uidUpdater;

    @Mock
    private TimeoutResolver timeoutResolver;

    @Mock
    private TimeoutFactory timeoutFactory;

    @Mock
    private BidRequestOrtbVersionConversionManager ortbVersionConversionManager;

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
    private HookStageExecutor hookStageExecutor;

    @Mock
    private HttpInteractionLogger httpInteractionLogger;

    @Mock
    private PriceFloorAdjuster priceFloorAdjuster;

    @Mock
    private PriceFloorEnforcer priceFloorEnforcer;

    @Mock
    private DsaEnforcer dsaEnforcer;

    @Mock
    private BidAdjustmentFactorResolver bidAdjustmentFactorResolver;

    @Mock
    private Metrics metrics;

    @Mock
    private UidsCookie uidsCookie;

    @Mock
    private Timeout timeout;

    @Mock
    private CriteriaLogManager criteriaLogManager;

    @Mock
    private ActivityInfrastructure activityInfrastructure;

    private Clock clock;

    private ExchangeService target;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        given(bidResponseCreator.create(any(), any(), any()))
                .willReturn(Future.succeededFuture(givenBidResponseWithBids(singletonList(givenBid(identity())))));

        given(bidderCatalog.isValidName(anyString())).willReturn(true);
        given(bidderCatalog.isActive(anyString())).willReturn(true);
        given(bidderCatalog.usersyncerByName(anyString()))
                .willReturn(Optional.of(Usersyncer.of("cookieFamily", null, null)));
        given(bidderCatalog.bidderInfoByName(anyString())).willReturn(BidderInfo.create(
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                false,
                false,
                CompressionType.NONE,
                Ortb.of(false)));

        given(privacyEnforcementService.mask(any(), argThat(MapUtils::isNotEmpty), any()))
                .willAnswer(inv ->
                        Future.succeededFuture(((Map<String, User>) inv.getArgument(1)).entrySet().stream()
                                .map(bidderAndUser -> BidderPrivacyResult.builder()
                                        .requestBidder(bidderAndUser.getKey())
                                        .user(bidderAndUser.getValue())
                                        .build())
                                .toList()));

        given(privacyEnforcementService.mask(any(), argThat(MapUtils::isEmpty), any()))
                .willReturn(Future.succeededFuture(emptyList()));

        given(fpdResolver.resolveUser(any(), any())).willAnswer(invocation -> invocation.getArgument(0));
        given(fpdResolver.resolveSite(any(), any())).willAnswer(invocation -> invocation.getArgument(0));
        given(fpdResolver.resolveDooh(any(), any())).willAnswer(invocation -> invocation.getArgument(0));
        given(fpdResolver.resolveApp(any(), any())).willAnswer(invocation -> invocation.getArgument(0));
        given(fpdResolver.resolveImpExt(any(), anyBoolean()))
                .willAnswer(invocation -> invocation.getArgument(0));

        given(supplyChainResolver.resolveForBidder(anyString(), any())).willReturn(null);

        given(hookStageExecutor.executeBidderRequestStage(any(), any()))
                .willAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(
                        false,
                        BidderRequestPayloadImpl.of(invocation.<BidderRequest>getArgument(0).getBidRequest()))));
        given(hookStageExecutor.executeRawBidderResponseStage(any(), any()))
                .willAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(
                        false,
                        BidderResponsePayloadImpl.of(invocation.<BidderResponse>getArgument(0).getSeatBid()
                                .getBids()))));
        given(hookStageExecutor.executeAuctionResponseStage(any(), any()))
                .willAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(
                        false,
                        AuctionResponsePayloadImpl.of(invocation.getArgument(0)))));

        given(mediaTypeProcessor.process(any(), anyString(), any(), any()))
                .willAnswer(invocation -> MediaTypeProcessingResult.succeeded(invocation.getArgument(0), emptyList()));

        given(responseBidValidator.validate(any(), any(), any(), any())).willReturn(ValidationResult.success());

        given(currencyService.convertCurrency(any(), any(), any(), any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(uidUpdater.updateUid(any(), any(), any()))
                .willAnswer(inv -> Optional.ofNullable((AuctionContext) inv.getArgument(1))
                        .map(AuctionContext::getBidRequest)
                        .map(BidRequest::getUser)
                        .map(user -> UpdateResult.updated(null))
                        .orElse(UpdateResult.unaltered(null)));

        given(storedResponseProcessor.getStoredResponseResult(any(), any()))
                .willAnswer(inv -> Future.succeededFuture(StoredResponseResult.of(inv.getArgument(0), emptyList(),
                        emptyMap())));
        given(storedResponseProcessor.mergeWithBidderResponses(any(), any(), any()))
                .willAnswer(inv -> inv.getArgument(0));
        given(storedResponseProcessor.updateStoredBidResponse(any()))
                .willAnswer(inv -> inv.getArgument(0));

        given(priceFloorEnforcer.enforce(any(), any(), any(), any())).willAnswer(inv -> inv.getArgument(1));
        given(dsaEnforcer.enforce(any(), any(), any())).willAnswer(inv -> inv.getArgument(1));
        given(priceFloorAdjuster.adjustForImp(any(), any(), any(), any()))
                .willAnswer(inv -> ((Imp) inv.getArgument(0)).getBidfloor());

        given(bidAdjustmentFactorResolver.resolve(any(ImpMediaType.class), any(), any())).willReturn(null);

        given(criteriaLogManager.traceResponse(any(), any(), any(), anyBoolean()))
                .willAnswer(inv -> inv.getArgument(1));

        given(timeoutResolver.adjustForBidder(anyLong(), anyInt(), anyLong()))
                .willAnswer(invocation -> invocation.getArgument(0));

        given(timeoutResolver.adjustForRequest(anyLong(), anyLong()))
                .willAnswer(invocation -> invocation.getArgument(0));

        given(timeoutFactory.create(anyLong()))
                .willReturn(timeout);

        given(timeoutFactory.create(anyLong(), anyLong()))
                .willReturn(timeout);

        given(ortbVersionConversionManager.convertFromAuctionSupportedVersion(any(), any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        given(activityInfrastructure.isAllowed(any(), any()))
                .willReturn(true);

        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        givenTarget(false);
    }

    @Test
    public void shouldTolerateImpWithoutExtension() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(null));

        // when
        final AuctionContext result = target.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        verifyNoInteractions(bidderCatalog);
        verifyNoInteractions(httpBidderRequester);
        assertThat(result).extracting(AuctionContext::getBidResponse).isNotNull();
    }

    @Test
    public void shouldTolerateImpWithUnknownBidderInExtension() {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(false);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("invalid", 0)));

        // when
        final AuctionContext result = target.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        verify(bidderCatalog, times(2)).isValidName(eq("invalid"));
        verifyNoInteractions(httpBidderRequester);
        assertThat(result).extracting(AuctionContext::getBidResponse).isNotNull();
    }

    @Test
    public void shouldSkipBidderDisallowedByActivityInfrastructure() {
        // given
        given(activityInfrastructure.isAllowed(
                eq(Activity.CALL_BIDDER),
                argThat(argument -> argument.componentType().equals(ComponentType.BIDDER)
                        && argument.componentName().equals("disallowed"))))
                .willReturn(false);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("disallowed", 0)));

        // when
        final AuctionContext result = target.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        verifyNoInteractions(httpBidderRequester);
        assertThat(result).extracting(AuctionContext::getBidResponse).isNotNull();
    }

    @Test
    public void shouldTolerateMissingPrebidImpExtension() {
        // given
        givenBidder(givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final BidRequest capturedBidRequest = captureBidRequest();
        assertThat(capturedBidRequest.getImp()).hasSize(1)
                .element(0)
                .returns(mapper.valueToTree(ExtPrebid.of(null, 1)), Imp::getExt);
    }

    @Test
    public void shouldExtractRequestWithBidderSpecificExtension() {
        // given
        givenBidder(givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(singletonList(
                        givenImp(singletonMap("someBidder", 1), builder -> builder
                                .id("impId")
                                .banner(Banner.builder()
                                        .format(singletonList(Format.builder().w(400).h(300).build()))
                                        .build()))),
                builder -> builder.id("requestId").tmax(500L));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

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
                        .ext(mapper.valueToTree(ExtPrebid.of(null, 1)))
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
                        givenImp(singletonMap("someBidder", 1), builder -> builder
                                .id("impId")
                                .banner(Banner.builder()
                                        .format(singletonList(Format.builder().w(400).h(300).build()))
                                        .build()))),
                builder -> builder
                        .id("requestId")
                        .ext(ExtRequest.of(
                                ExtRequestPrebid.builder()
                                        .currency(ExtRequestCurrency.of(currencyRates, false))
                                        .build()))
                        .tmax(500L));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

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
                        .ext(mapper.valueToTree(ExtPrebid.of(null, 1)))
                        .build()))
                .ext(ExtRequest.of(
                        ExtRequestPrebid.builder().currency(ExtRequestCurrency.of(currencyRates, false)).build()))
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
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidRequest1Captor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(same(bidder1), bidRequest1Captor.capture(), any(), any(), any(), any(), anyBoolean());
        final BidderRequest capturedBidRequest1 = bidRequest1Captor.getValue();
        assertThat(capturedBidRequest1.getBidRequest().getImp()).hasSize(2)
                .extracting(imp -> imp.getExt().get("bidder").asInt())
                .containsOnly(1, 3);

        final ArgumentCaptor<BidderRequest> bidRequest2Captor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(same(bidder2), bidRequest2Captor.capture(), any(), any(), any(), any(), anyBoolean());
        final BidderRequest capturedBidRequest2 = bidRequest2Captor.getValue();
        assertThat(capturedBidRequest2.getBidRequest().getImp()).hasSize(1)
                .element(0).returns(2, imp -> imp.getExt().get("bidder").asInt());
    }

    @Test
    public void shouldSkipBidderWhenRejectedByBidderRequestHooks() {
        // given
        doAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(true, null)))
                .when(hookStageExecutor).executeBidderRequestStage(any(), any());

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)), identity());

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        verifyNoInteractions(httpBidderRequester);
    }

    @Test
    public void shouldPassRequestModifiedByBidderRequestHooks() {
        // given
        givenBidder(givenEmptySeatBid());

        doAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(
                false,
                BidderRequestPayloadImpl.of(BidRequest.builder().id("bidderRequestId").build()))))
                .when(hookStageExecutor).executeBidderRequestStage(any(), any());

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)), identity());

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final BidRequest capturedBidRequest = captureBidRequest();
        assertThat(capturedBidRequest).isEqualTo(BidRequest.builder().id("bidderRequestId").build());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldSkipBidderWhenRejectedByRawBidderResponseHooks() {
        // given
        final String bidder = "someBidder";
        givenBidder(bidder, mock(Bidder.class), givenSeatBid(singletonList(
                givenBidderBid(Bid.builder().price(BigDecimal.ONE).build()))));

        doAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(true, null)))
                .when(hookStageExecutor).executeRawBidderResponseStage(any(), any());

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap(bidder, 1)), identity());

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<List<AuctionParticipation>> auctionParticipationCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(storedResponseProcessor).mergeWithBidderResponses(auctionParticipationCaptor.capture(), any(), any());

        assertThat(auctionParticipationCaptor.getValue())
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .containsOnly(BidderSeatBid.empty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldPassRequestModifiedByRawBidderResponseHooks() {
        // given
        final String bidder = "someBidder";
        givenBidder(bidder, mock(Bidder.class), givenSeatBid(singletonList(
                givenBidderBid(Bid.builder().build()))));

        final BidderBid hookChangedBid = BidderBid.of(Bid.builder().id("newId").build(), video, "USD");
        doAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(
                false,
                BidderResponsePayloadImpl.of(singletonList(hookChangedBid)))))
                .when(hookStageExecutor).executeRawBidderResponseStage(any(), any());

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap(bidder, 1)), identity());

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<List<AuctionParticipation>> auctionParticipationCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(storedResponseProcessor).mergeWithBidderResponses(auctionParticipationCaptor.capture(), any(), any());

        assertThat(auctionParticipationCaptor.getValue())
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .containsOnly(hookChangedBid);
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

        final ExtRequest extRequest = ExtRequest.of(
                ExtRequestPrebid.builder()
                        .bidders(mapper.createObjectNode()
                                .putPOJO(bidder1Name, mapper.createObjectNode().put("test1", "test1"))
                                .putPOJO(bidder2Name, mapper.createObjectNode().put("test2", "test2"))
                                .putPOJO("spam", mapper.createObjectNode().put("spam", "spam")))
                        .auctiontimestamp(1000L)
                        .build());

        final BidRequest bidRequest = givenBidRequest(asList(
                        givenImp(singletonMap(bidder1Name, 1), identity()),
                        givenImp(singletonMap(bidder2Name, 2), identity())),
                builder -> builder.ext(extRequest));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidRequest1Captor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(same(bidder1), bidRequest1Captor.capture(), any(), any(), any(), any(), anyBoolean());

        final BidderRequest capturedBidRequest1 = bidRequest1Captor.getValue();
        final ExtRequestPrebid prebid1 = capturedBidRequest1.getBidRequest().getExt().getPrebid();
        assertThat(prebid1).isNotNull();
        final JsonNode bidders1 = prebid1.getBidders();
        assertThat(bidders1).isNotNull();
        assertThat(bidders1.fields()).toIterable().hasSize(1)
                .containsOnly(entry("bidder", mapper.createObjectNode().put("test1", "test1")));

        final ArgumentCaptor<BidderRequest> bidRequest2Captor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(same(bidder2), bidRequest2Captor.capture(), any(), any(), any(), any(), anyBoolean());
        final BidRequest capturedBidRequest2 = bidRequest2Captor.getValue().getBidRequest();
        final ExtRequestPrebid prebid2 = capturedBidRequest2.getExt().getPrebid();
        assertThat(prebid2).isNotNull();
        final JsonNode bidders2 = prebid2.getBidders();
        assertThat(bidders2).isNotNull();
        assertThat(bidders2.fields()).toIterable().hasSize(1)
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

        final SupplyChainNode specificNodes = SupplyChainNode.of("asi", "sid", "rid", "name", "domain", 1, null);
        final SupplyChain specificSchain = SupplyChain.of(1, singletonList(specificNodes), "ver", null);
        final ExtRequestPrebidSchain schainForBidders = ExtRequestPrebidSchain.of(
                asList(bidder1Name, bidder2Name), specificSchain);

        final SupplyChainNode generalNodes = SupplyChainNode.of("t", null, "a", null, "ads", 0, null);
        final SupplyChain generalSchain = SupplyChain.of(123, singletonList(generalNodes), "t", null);
        final ExtRequestPrebidSchain allSchain = ExtRequestPrebidSchain.of(singletonList("*"), generalSchain);

        final ExtRequest extRequest = ExtRequest.of(
                ExtRequestPrebid.builder()
                        .schains(asList(schainForBidders, allSchain))
                        .auctiontimestamp(1000L)
                        .build());
        final BidRequest bidRequest = givenBidRequest(
                asList(
                        givenImp(singletonMap(bidder1Name, 1), identity()),
                        givenImp(singletonMap(bidder2Name, 2), identity()),
                        givenImp(singletonMap(bidder3Name, 3), identity())),
                builder -> builder.ext(extRequest));

        given(supplyChainResolver.resolveForBidder(eq("bidder1"), same(bidRequest))).willReturn(specificSchain);
        given(supplyChainResolver.resolveForBidder(eq("bidder2"), same(bidRequest))).willReturn(specificSchain);
        given(supplyChainResolver.resolveForBidder(eq("bidder3"), same(bidRequest))).willReturn(generalSchain);

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidRequest1Captor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(same(bidder1), bidRequest1Captor.capture(), any(), any(), any(), any(), anyBoolean());
        final BidRequest capturedBidRequest1 = bidRequest1Captor.getValue().getBidRequest();
        final SupplyChain requestSchain1 = capturedBidRequest1.getSource().getSchain();
        assertThat(requestSchain1).isNotNull();
        assertThat(requestSchain1).isEqualTo(specificSchain);
        assertThat(capturedBidRequest1.getExt().getPrebid().getSchains()).isNull();

        final ArgumentCaptor<BidderRequest> bidRequest2Captor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(same(bidder2), bidRequest2Captor.capture(), any(), any(), any(), any(), anyBoolean());
        final BidRequest capturedBidRequest2 = bidRequest2Captor.getValue().getBidRequest();
        final SupplyChain requestSchain2 = capturedBidRequest2.getSource().getSchain();
        assertThat(requestSchain2).isNotNull();
        assertThat(requestSchain2).isEqualTo(specificSchain);
        assertThat(capturedBidRequest2.getExt().getPrebid().getSchains()).isNull();

        final ArgumentCaptor<BidderRequest> bidRequest3Captor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(same(bidder3), bidRequest3Captor.capture(), any(), any(), any(), any(), anyBoolean());
        final BidRequest capturedBidRequest3 = bidRequest3Captor.getValue().getBidRequest();
        final SupplyChain requestSchain3 = capturedBidRequest3.getSource().getSchain();
        assertThat(requestSchain3).isNotNull();
        assertThat(requestSchain3).isEqualTo(generalSchain);
        assertThat(capturedBidRequest3.getExt().getPrebid().getSchains()).isNull();
    }

    @Test
    public void shouldRemoveTidsIfCreateTidsFalse() {
        // given
        final String bidderName = "bidder";
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder(bidderName, bidder, givenEmptySeatBid());

        final Imp imp = givenImp(Map.of(bidderName, 1), identity());
        imp.getExt().put("tid", "bidderTidValue");
        final BidRequest bidRequest = givenBidRequest(
                singletonList(imp),
                builder -> builder
                        .source(Source.builder().tid("sourceTidValue").build())
                        .ext(ExtRequest.of(
                                ExtRequestPrebid.builder()
                                        .createTids(false)
                                        .build())));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(same(bidder), bidRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());

        final BidRequest capturedBidRequest = bidRequestCaptor.getValue().getBidRequest();
        assertThat(capturedBidRequest)
                .extracting(BidRequest::getSource)
                .extracting(Source::getTid)
                .isNull();
        assertThat(capturedBidRequest.getImp())
                .extracting(Imp::getExt)
                .extracting(ext -> ext.get("tid"))
                .containsOnlyNulls();
    }

    @Test
    public void shouldPassTidsIfCreateTidsTrue() {
        // given
        final String bidderName = "bidder";
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder(bidderName, bidder, givenEmptySeatBid());

        final Imp imp = givenImp(Map.of(bidderName, 1), identity());
        imp.getExt().put("tid", "bidderTidValue");
        final BidRequest bidRequest = givenBidRequest(
                singletonList(imp),
                builder -> builder
                        .source(Source.builder().tid("sourceTidValue").build())
                        .ext(ExtRequest.of(
                                ExtRequestPrebid.builder()
                                        .createTids(true)
                                        .build())));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(same(bidder), bidRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());

        final BidRequest capturedBidRequest = bidRequestCaptor.getValue().getBidRequest();
        assertThat(capturedBidRequest)
                .extracting(BidRequest::getSource)
                .extracting(Source::getTid)
                .isEqualTo("sourceTidValue");
        assertThat(capturedBidRequest.getImp())
                .extracting(Imp::getExt)
                .extracting(ext -> ext.get("tid"))
                .containsExactly(TextNode.valueOf("bidderTidValue"));
    }

    @Test
    public void shouldRemoveTidsIfCreateTidsFalseAndKeepReceivedSchain() {
        // given
        final String bidderName = "bidder";
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder(bidderName, bidder, givenEmptySeatBid());

        final Imp imp = givenImp(Map.of(bidderName, 1), identity());
        imp.getExt().put("tid", "bidderTidValue");

        final BidRequest bidRequest = givenBidRequest(
                singletonList(imp),
                builder -> builder
                        .source(Source.builder()
                                .tid("sourceTidValue")
                                .schain(SupplyChain.of(
                                        1,
                                        List.of(SupplyChainNode.of("freestar.com", "66", null, null, null, 1, null)),
                                        "1.0",
                                        null))
                                .build())
                        .ext(ExtRequest.of(
                                ExtRequestPrebid.builder()
                                        .createTids(false)
                                        .build())));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(same(bidder), bidRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());

        final BidRequest capturedBidRequest = bidRequestCaptor.getValue().getBidRequest();
        assertThat(capturedBidRequest)
                .extracting(BidRequest::getSource)
                .satisfies(source -> {
                    assertThat(source.getTid()).isNull();
                    assertThat(source.getSchain()).isNotNull();
                });
        assertThat(capturedBidRequest.getImp())
                .extracting(Imp::getExt)
                .extracting(ext -> ext.get("tid"))
                .containsOnlyNulls();
    }

    @Test
    public void shouldRemoveTidsIfTransmitTidActivityDisallowed() {
        // given
        final String bidderName = "bidder";
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder(bidderName, bidder, givenEmptySeatBid());

        given(activityInfrastructure.isAllowed(eq(Activity.TRANSMIT_TID), any())).willReturn(false);

        final Imp imp = givenImp(Map.of(bidderName, 1), identity());
        imp.getExt().put("tid", "bidderTidValue");
        final BidRequest bidRequest = givenBidRequest(
                singletonList(imp),
                builder -> builder.source(Source.builder().tid("sourceTidValue").build()));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(same(bidder), bidRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());

        final BidRequest capturedBidRequest = bidRequestCaptor.getValue().getBidRequest();
        assertThat(capturedBidRequest)
                .extracting(BidRequest::getSource)
                .extracting(Source::getTid)
                .isNull();
        assertThat(capturedBidRequest.getImp())
                .extracting(Imp::getExt)
                .extracting(ext -> ext.get("tid"))
                .containsOnlyNulls();
    }

    @Test
    public void shouldPassTidsIfTransmitTidActivityAllowed() {
        // given
        final String bidderName = "bidder";
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder(bidderName, bidder, givenEmptySeatBid());

        given(activityInfrastructure.isAllowed(eq(Activity.TRANSMIT_TID), any())).willReturn(true);

        final Imp imp = givenImp(Map.of(bidderName, 1), identity());
        imp.getExt().put("tid", "bidderTidValue");
        final BidRequest bidRequest = givenBidRequest(
                singletonList(imp),
                builder -> builder.source(Source.builder().tid("sourceTidValue").build()));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(same(bidder), bidRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());

        final BidRequest capturedBidRequest = bidRequestCaptor.getValue().getBidRequest();
        assertThat(capturedBidRequest)
                .extracting(BidRequest::getSource)
                .extracting(Source::getTid)
                .isEqualTo("sourceTidValue");
        assertThat(capturedBidRequest.getImp())
                .extracting(Imp::getExt)
                .extracting(ext -> ext.get("tid"))
                .containsExactly(TextNode.valueOf("bidderTidValue"));
    }

    @Test
    public void shouldReturnFailedFutureWithUnchangedMessageWhenPrivacyEnforcementServiceFails() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());

        given(privacyEnforcementService.mask(any(), any(), any()))
                .willReturn(Future.failedFuture("Error when retrieving allowed purpose ids"));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .regs(Regs.builder().gdpr(1).build()));

        // when
        final Future<?> result = target.holdAuction(givenRequestContext(bidRequest));

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).hasMessage("Error when retrieving allowed purpose ids");
    }

    @Test
    public void shouldNotCreateRequestForBidderRestrictedByPrivacyEnforcement() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenEmptySeatBid());

        final BidderPrivacyResult restrictedPrivacy = BidderPrivacyResult.builder()
                .requestBidder("bidderAlias")
                .blockedRequestByTcf(true)
                .build();
        given(privacyEnforcementService.mask(any(), any(), any()))
                .willReturn(Future.succeededFuture(singletonList(restrictedPrivacy)));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                        givenImp(singletonMap("bidderAlias", 1), identity())),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("bidderAlias", "bidder"))
                        .build())));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        verifyNoInteractions(httpBidderRequester);
    }

    @Test
    public void shouldReturnProperStoredResponseIfAvailableOnlySingleImpRequests() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder-test", bidder, givenEmptySeatBid());

        given(storedResponseProcessor.getStoredResponseResult(anyList(), any()))
                .willReturn(Future.succeededFuture(
                        StoredResponseResult.of(singletonList(
                                        givenImp("", impBuilder -> impBuilder.id("test-1-key"))),
                                singletonList(SeatBid.builder().build()),
                                singletonMap("test-1-key", singletonMap("bidder-test", "test-second-value")))));

        final BidderPrivacyResult bidderPrivacyResult = BidderPrivacyResult.builder()
                .requestBidder("bidder-test")
                .build();
        given(privacyEnforcementService.mask(any(), any(), any()))
                .willReturn(Future.succeededFuture(singletonList(bidderPrivacyResult)));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                        givenImp(singletonMap("bidderAlias", 1), identity())),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("bidderAlias", "bidder-test"))
                        .auctiontimestamp(1000L)
                        .build())));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(same(bidder), bidRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());
        assertThat(bidRequestCaptor.getValue().getStoredResponse())
                .contains("test-second-value");
    }

    @Test
    public void shouldExtractRequestByAliasForCorrectBidder() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(singletonList(
                        givenImp(singletonMap("bidderAlias", 1), identity())),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("bidderAlias", "bidder"))
                        .auctiontimestamp(1000L)
                        .build())));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(same(bidder), bidRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());
        assertThat(bidRequestCaptor.getValue().getBidRequest().getImp()).hasSize(1)
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
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("bidderAlias", "bidder"))
                        .auctiontimestamp(1000L)
                        .build())));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester, times(2))
                .requestBids(same(bidder), bidRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());
        final List<BidderRequest> capturedBidderRequests = bidRequestCaptor.getAllValues();

        assertThat(capturedBidderRequests).hasSize(2)
                .extracting(BidderRequest::getBidRequest)
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
        final AuctionContext result = target.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(result.getBidResponse().getSeatbid()).isEmpty();
    }

    @Test
    public void shouldReturnSeparateSeatBidsForTheSameBidderIfBiddersAliasAndBidderWereUsedWithinSingleImp() {
        // given
        given(httpBidderRequester.requestBids(
                any(),
                eq(BidderRequest.builder()
                        .bidder("bidder")
                        .bidRequest(givenBidRequest(
                                singletonList(givenImp(
                                        null,
                                        builder -> builder
                                                .id("1")
                                                .ext(mapper.valueToTree(ExtPrebid.of(null, 1))))),
                                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                                        .auctiontimestamp(1000L)
                                        .build()))))
                        .build()),
                any(),
                any(),
                any(),
                any(),
                anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(singletonList(
                        givenBidderBid(Bid.builder().impid("1").price(BigDecimal.ONE).build())))));

        given(httpBidderRequester.requestBids(
                any(),
                eq(BidderRequest.builder()
                        .bidder("bidderAlias")
                        .bidRequest(givenBidRequest(
                                singletonList(givenImp(
                                        null,
                                        builder -> builder
                                                .id("1")
                                                .ext(mapper.valueToTree(ExtPrebid.of(null, 2))))),
                                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                                        .auctiontimestamp(1000L)
                                        .build()))))
                        .build()),
                any(),
                any(),
                any(),
                any(),
                anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(singletonList(
                        givenBidderBid(Bid.builder().impid("1").price(BigDecimal.ONE).build())))));

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp("1", doubleMap("bidder", 1, "bidderAlias", 2)),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("bidderAlias", "bidder"))
                        .auctiontimestamp(1000L)
                        .build())));

        given(bidResponseCreator.create(any(), any(), any()))
                .willReturn(Future.succeededFuture(BidResponse.builder()
                        .seatbid(asList(
                                givenSeatBid(singletonList(givenBid(identity())), identity()),
                                givenSeatBid(singletonList(givenBid(identity())), identity())))
                        .build()));

        // when
        final AuctionContext result = target.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        verify(httpBidderRequester, times(2))
                .requestBids(any(), any(), any(), any(), any(), any(), anyBoolean());
        assertThat(result.getBidResponse().getSeatbid()).hasSize(2)
                .extracting(seatBid -> seatBid.getBid().size())
                .containsOnly(1, 1);
    }

    @Test
    public void shouldPropagateFledgeResponseWithBidderAlias() {
        // given
        final FledgeAuctionConfig fledgeAuctionConfig = givenFledgeAuctionConfig("impId");
        given(httpBidderRequester.requestBids(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenEmptySeatBid()
                        .toBuilder()
                        .fledgeAuctionConfigs(List.of(fledgeAuctionConfig))
                        .build()));

        final BidRequest bidRequest = givenBidRequest(
                singletonList(Imp.builder()
                        .id("impId")
                        .ext(mapper.valueToTree(
                                doubleMap("prebid", singletonMap("bidder", singletonMap("bidderAlias", 1)),
                                        "ae", 1)))
                        .build()),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("bidderAlias", "bidder"))
                        .build())));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        verify(httpBidderRequester, times(1))
                .requestBids(any(), any(), any(), any(), any(), any(), anyBoolean());

        // then
        final BidRequest capturedBidRequest = captureBidRequest();

        assertThat(capturedBidRequest.getImp())
                .extracting(Imp::getExt)
                .containsOnly(mapper.valueToTree(ExtPrebid.of(null, 1,
                        ExtImpAuctionEnvironment.ON_DEVICE_IG_AUCTION_FLEDGE)));

        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipations();

        assertThat(auctionParticipations)
                .hasSize(1)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .extracting(BidderSeatBid::getFledgeAuctionConfigs)
                .containsExactly(List.of(fledgeAuctionConfig));
    }

    @Test
    public void shouldOverrideDebugEnabledFlag() {
        // given
        given(bidderCatalog.isDebugAllowed(anyString())).willReturn(false);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder", 2)));
        final AuctionContext auctionContext = givenRequestContext(bidRequest).toBuilder()
                .debugContext(DebugContext.of(true, true, null))
                .build();

        given(debugResolver.resolveDebugForBidder(any(), eq("bidder")))
                .willReturn(true);

        given(httpBidderRequester.requestBids(any(), any(), any(), any(), any(), any(), eq(true)))
                .willReturn(Future.succeededFuture(BidderSeatBid.builder()
                        .httpCalls(singletonList(ExtHttpCall.builder().build()))
                        .build()));

        given(bidResponseCreator.create(any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        BidResponse.builder()
                                .ext(ExtBidResponse.builder()
                                        .debug(ExtResponseDebug.of(null, null, null))
                                        .build())
                                .build()));

        // when
        final AuctionContext result = target.holdAuction(auctionContext).result();

        // then
        verify(httpBidderRequester).requestBids(any(), any(), any(), any(), any(), any(), eq(true));

        final ArgumentCaptor<AuctionContext> captor = ArgumentCaptor.forClass(AuctionContext.class);
        verify(bidResponseCreator).create(captor.capture(), any(), anyMap());
        assertThat(captor.getValue().getDebugContext()).isEqualTo(DebugContext.of(true, true, null));

        assertThat(result.getBidResponse().getExt().getDebug()).isNotNull();
    }

    @Test
    public void shouldAddDebugInfoIfDebugEnabledAndPublisherAndBidderAllowedDebug() {
        // given
        final BidderSeatBid bidderSeatBid = BidderSeatBid.builder()
                .httpCalls(singletonList(ExtHttpCall.builder().build()))
                .build();
        given(httpBidderRequester.requestBids(any(), any(), any(), any(), any(), any(), eq(true)))
                .willReturn(Future.succeededFuture(bidderSeatBid));

        given(bidResponseCreator.create(any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        BidResponse.builder()
                                .ext(ExtBidResponse.builder()
                                        .debug(ExtResponseDebug.of(null, null, null))
                                        .build())
                                .build()));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder", 2)));
        final AuctionContext auctionContext = givenRequestContext(bidRequest).toBuilder()
                .debugContext(DebugContext.of(true, true, null))
                .build();
        given(debugResolver.resolveDebugForBidder(any(), eq("bidder")))
                .willReturn(true);

        // when
        final AuctionContext result = target.holdAuction(auctionContext).result();

        // then
        verify(httpBidderRequester).requestBids(any(), any(), any(), any(), any(), any(), eq(true));

        final ArgumentCaptor<AuctionContext> captor = ArgumentCaptor.forClass(AuctionContext.class);
        verify(bidResponseCreator).create(captor.capture(), any(), anyMap());
        assertThat(captor.getValue().getDebugContext())
                .isEqualTo(DebugContext.of(true, true, null));

        assertThat(result.getBidResponse().getExt().getDebug()).isNotNull();
    }

    @Test
    public void shouldNotAddDebugInfoIfPublisherIsNotAllowedToDebug() {
        // given
        final BidderSeatBid bidderSeatBid = BidderSeatBid.empty();
        given(httpBidderRequester.requestBids(any(), any(), any(), any(), any(), any(), eq(false)))
                .willReturn(Future.succeededFuture(bidderSeatBid));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder", 2)));
        final AuctionContext auctionContext = givenRequestContext(bidRequest).toBuilder()
                .debugContext(DebugContext.of(false, false, null))
                .build();
        given(debugResolver.resolveDebugForBidder(auctionContext, "bidder"))
                .willReturn(false);

        given(bidResponseCreator.create(any(), any(), any())).willReturn(
                Future.succeededFuture(BidResponse.builder().ext(ExtBidResponse.builder().build()).build()));

        // when
        final AuctionContext result = target.holdAuction(auctionContext).result();

        // then
        verify(httpBidderRequester).requestBids(any(), any(), any(), any(), any(), any(), eq(false));

        final ArgumentCaptor<AuctionContext> captor = ArgumentCaptor.forClass(AuctionContext.class);
        verify(bidResponseCreator).create(captor.capture(), any(), anyMap());
        assertThat(captor.getValue().getDebugContext()).isEqualTo(
                DebugContext.of(false, false, null));

        assertThat(result.getBidResponse().getExt().getDebug()).isNull();
    }

    @Test
    public void shouldNotAddDebugInfoIfBidderDisabledDebug() {
        // given
        final BidderSeatBid bidderSeatBid = BidderSeatBid.empty();
        given(httpBidderRequester.requestBids(any(), any(), any(), any(), any(), any(), eq(false)))
                .willReturn(Future.succeededFuture(bidderSeatBid));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder", 2)));
        final AuctionContext auctionContext = givenRequestContext(bidRequest).toBuilder()
                .debugContext(DebugContext.of(true, true, null))
                .build();
        given(debugResolver.resolveDebugForBidder(auctionContext, "bidder"))
                .willReturn(false);

        given(bidResponseCreator.create(any(), any(), any())).willReturn(
                Future.succeededFuture(BidResponse.builder().ext(ExtBidResponse.builder().build()).build()));

        // when
        final AuctionContext result = target.holdAuction(auctionContext).result();

        // then
        verify(httpBidderRequester).requestBids(any(), any(), any(), any(), any(), any(), eq(false));

        final ArgumentCaptor<AuctionContext> captor = ArgumentCaptor.forClass(AuctionContext.class);
        verify(bidResponseCreator).create(captor.capture(), any(), anyMap());
        assertThat(captor.getValue().getDebugContext()).isEqualTo(
                DebugContext.of(true, true, null));

        assertThat(result.getBidResponse().getExt().getDebug()).isNull();
    }

    @Test
    public void shouldCallBidResponseCreatorWithExpectedParamsAndUpdateDebugErrors() {
        // given
        givenBidder("bidder1", mock(Bidder.class), givenEmptySeatBid());

        final Bid thirdBid = Bid.builder().id("bidId3").impid("impId3").price(BigDecimal.valueOf(7.89)).build();
        givenBidder("bidder2", mock(Bidder.class), givenSeatBid(singletonList(givenBidderBid(thirdBid))));

        final ExtRequestPrebidMultiBid multiBid1 = ExtRequestPrebidMultiBid.of("bidder1", null, 2, "bi1");
        final ExtRequestPrebidMultiBid multiBid2 = ExtRequestPrebidMultiBid.of("bidder2", singletonList("invalid"), 4,
                "bi2");
        final ExtRequestPrebidMultiBid multiBid3 = ExtRequestPrebidMultiBid.of("bidder3", singletonList("invalid"),
                null, "bi3");
        final ExtRequestPrebidMultiBid duplicateMultiBid1 = ExtRequestPrebidMultiBid.of("bidder1", null, 100, "bi1_2");
        final ExtRequestPrebidMultiBid duplicateMultiBids1 = ExtRequestPrebidMultiBid.of(null, singletonList("bidder1"),
                100, "bi1_3");
        final ExtRequestPrebidMultiBid multiBid4 = ExtRequestPrebidMultiBid.of(null,
                Arrays.asList("bidder4", "bidder5"), 3, "ignored");
        final ExtRequestPrebidMultiBid multiBid5 = ExtRequestPrebidMultiBid.of("bidder6",
                Arrays.asList("bidder4", "bidder5"), 0, "bi6");
        final ExtRequestPrebidMultiBid multiBid6 = ExtRequestPrebidMultiBid.of(null,
                Collections.emptyList(), 0, "bi7");

        final ExtRequestTargeting targeting = givenTargeting(true);
        final ObjectNode events = mapper.createObjectNode();
        final BidRequest bidRequest = givenBidRequest(asList(
                        // imp ids are not really used for matching, included them here for clarity
                        givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1")),
                        givenImp(doubleMap("bidder1", 1, "bidder2", 2), builder -> builder.id("impId2"))),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(targeting)
                        .auctiontimestamp(1000L)
                        .events(events)
                        .multibid(Arrays.asList(multiBid1, multiBid2, multiBid3, duplicateMultiBid1,
                                duplicateMultiBids1, multiBid4, multiBid5, multiBid6))
                        .cache(ExtRequestPrebidCache.of(ExtRequestPrebidCacheBids.of(53, true),
                                ExtRequestPrebidCacheVastxml.of(34, true), true))
                        .build())));
        final AuctionContext auctionContext = givenRequestContext(bidRequest);

        // when
        target.holdAuction(auctionContext);

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

        final MultiBidConfig expectedMultiBid1 = MultiBidConfig.of(multiBid1.getBidder(), multiBid1.getMaxBids(),
                multiBid1.getTargetBidderCodePrefix());
        final MultiBidConfig expectedMultiBid2 = MultiBidConfig.of(multiBid2.getBidder(), multiBid2.getMaxBids(),
                multiBid2.getTargetBidderCodePrefix());
        final MultiBidConfig expectedFirstMultiBid4 = MultiBidConfig.of("bidder4", multiBid4.getMaxBids(), null);
        final MultiBidConfig expectedSecondMultiBid4 = MultiBidConfig.of("bidder5", multiBid4.getMaxBids(), null);
        final MultiBidConfig expectedFirstMultiBid5 = MultiBidConfig.of(multiBid5.getBidder(), 1, "bi6");

        final Map<String, MultiBidConfig> expectedMultiBidMap = new HashMap<>();
        expectedMultiBidMap.put(expectedMultiBid1.getBidder(), expectedMultiBid1);
        expectedMultiBidMap.put(expectedMultiBid2.getBidder(), expectedMultiBid2);
        expectedMultiBidMap.put(expectedFirstMultiBid4.getBidder(), expectedFirstMultiBid4);
        expectedMultiBidMap.put(expectedSecondMultiBid4.getBidder(), expectedSecondMultiBid4);
        expectedMultiBidMap.put(expectedFirstMultiBid5.getBidder(), expectedFirstMultiBid5);

        final ArgumentCaptor<AuctionContext> contextArgumentCaptor = ArgumentCaptor.forClass(AuctionContext.class);
        verify(bidResponseCreator)
                .create(contextArgumentCaptor.capture(), eq(expectedCacheInfo), eq(expectedMultiBidMap));

        final ObjectNode expectedBidExt = mapper.createObjectNode().put("origbidcpm", new BigDecimal("7.89"));
        final Bid expectedThirdBid = Bid.builder()
                .id("bidId3")
                .impid("impId3")
                .price(BigDecimal.valueOf(7.89))
                .ext(expectedBidExt)
                .build();
        final List<AuctionParticipation> auctionParticipations =
                contextArgumentCaptor.getValue().getAuctionParticipations();
        assertThat(auctionParticipations)
                .extracting(AuctionParticipation::getBidderResponse)
                .containsOnly(
                        BidderResponse.of(
                                "bidder2",
                                BidderSeatBid.of(singletonList(BidderBid.of(expectedThirdBid, banner, null))),
                                0),
                        BidderResponse.of("bidder1", BidderSeatBid.empty(), 0));

        final AuctionContext expectedAuctionContext = auctionContext.toBuilder()
                .auctionParticipations(auctionParticipations)
                .debugWarnings(asList(
                        "Invalid MultiBid: bidder bidder2 and bidders [invalid] specified."
                                + " Only bidder bidder2 will be used.",
                        "Invalid MultiBid: bidder bidder3 and bidders [invalid] specified."
                                + " Only bidder bidder3 will be used.",
                        "Invalid MultiBid: MaxBids for bidder bidder3 is not specified and will be skipped.",
                        "Invalid MultiBid: Bidder bidder1 specified multiple times.",
                        "Invalid MultiBid: CodePrefix bi1_3 that was specified for bidders [bidder1] will be skipped.",
                        "Invalid MultiBid: Bidder bidder1 specified multiple times.",
                        "Invalid MultiBid: CodePrefix ignored that was specified for bidders [bidder4, bidder5]"
                                + " will be skipped.",
                        "Invalid MultiBid: bidder bidder6 and bidders [bidder4, bidder5]"
                                + " specified. Only bidder bidder6 will be used.",
                        "Invalid MultiBid: Bidder and bidders was not specified."))
                .build();
        assertThat(contextArgumentCaptor.getValue()).isEqualTo(expectedAuctionContext);
    }

    @Test
    public void shouldCallBidResponseCreatorWithWinningOnlyTrueWhenIncludeBidderKeysIsFalse() {
        // given
        givenBidder("bidder1", mock(Bidder.class), givenEmptySeatBid());

        final Bid thirdBid = Bid.builder().id("bidId3").impid("impId3").price(BigDecimal.valueOf(7.89)).build();
        givenBidder("bidder2", mock(Bidder.class), givenSeatBid(singletonList(givenBidderBid(thirdBid))));

        final ExtRequestTargeting targeting = givenTargeting(false);

        final BidRequest bidRequest = givenBidRequest(asList(
                        // imp ids are not really used for matching, included them here for clarity
                        givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1")),
                        givenImp(doubleMap("bidder1", 1, "bidder2", 2), builder -> builder.id("impId2"))),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(targeting)
                        .cache(ExtRequestPrebidCache.of(null, null, true))
                        .auctiontimestamp(1000L)
                        .build())));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<AuctionContext> auctionContextArgumentCaptor =
                ArgumentCaptor.forClass(AuctionContext.class);
        verify(bidResponseCreator).create(
                auctionContextArgumentCaptor.capture(),
                eq(BidRequestCacheInfo.builder().doCaching(true).shouldCacheWinningBidsOnly(true).build()),
                eq(emptyMap()));

        assertThat(singletonList(auctionContextArgumentCaptor.getValue().getBidRequest()))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getCache)
                .extracting(ExtRequestPrebidCache::getWinningonly)
                .containsOnly(true);
    }

    @Test
    public void shouldCallBidResponseCreatorWithWinningOnlyFalseWhenWinningOnlyIsNull() {
        // given
        givenBidder("bidder1", mock(Bidder.class), givenEmptySeatBid());

        final Bid thirdBid = Bid.builder().id("bidId3").impid("impId3").price(BigDecimal.valueOf(7.89)).build();
        givenBidder("bidder2", mock(Bidder.class), givenSeatBid(singletonList(givenBidderBid(thirdBid))));

        final ExtRequestTargeting targeting = givenTargeting(false);

        final BidRequest bidRequest = givenBidRequest(asList(
                        // imp ids are not really used for matching, included them here for clarity
                        givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1")),
                        givenImp(doubleMap("bidder1", 1, "bidder2", 2), builder -> builder.id("impId2"))),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(targeting)
                        .cache(ExtRequestPrebidCache.of(null, null, null))
                        .auctiontimestamp(1000L)
                        .build())));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        verify(bidResponseCreator).create(any(), eq(BidRequestCacheInfo.builder().build()), eq(emptyMap()));
    }

    @Test
    public void shouldTolerateNullRequestExtPrebid() {
        // given
        givenBidder(givenSingleSeatBid(givenBidderBid(Bid.builder().impid("impId").price(BigDecimal.ONE).build())));

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.ext(jacksonMapper.fillExtension(ExtRequest.empty(), singletonMap("someField", 1))));

        // when
        final AuctionContext result = target.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(result.getBidResponse().getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(bid -> toExtBidPrebid(bid.getExt()).getTargeting())
                .allSatisfy(map -> assertThat(map).isNull());
    }

    @Test
    public void shouldTolerateNullRequestExtPrebidTargeting() {
        // given
        givenBidder(givenSingleSeatBid(givenBidderBid(Bid.builder().impid("impId").price(BigDecimal.ONE).build())));

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(singletonList("someBidder"), null))
                        .auctiontimestamp(1000L)
                        .build())));

        // when
        final AuctionContext result = target.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(result.getBidResponse().getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(bid -> toExtBidPrebid(bid.getExt()).getTargeting())
                .allSatisfy(map -> assertThat(map).isNull());
    }

    @Test
    public void shouldTolerateResponseBidValidationErrors() {
        // given
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(singletonList(
                givenBidderBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(1.23)).build()))));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                        // imp ids are not really used for matching, included them here for clarity
                        givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1"))),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .auctiontimestamp(1000L)
                        .build())));

        given(responseBidValidator.validate(any(), any(), any(), any())).willReturn(ValidationResult.error(
                singletonList("bid validation warning"),
                "bid validation error"));

        givenBidResponseCreator(singletonList(Bid.builder().build()));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipations();
        assertThat(auctionParticipations)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .isEmpty();
        assertThat(auctionParticipations)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getErrors)
                .containsOnly(
                        BidderError.invalidBid("BidId `bidId1` validation messages: Error: bid validation error."
                                + " Warning: bid validation warning"));
    }

    @Test
    public void shouldTolerateResponseBidValidationWarnings() {
        // given
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(singletonList(
                givenBidderBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(1.23)).build()))));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                        // imp ids are not really used for matching, included them here for clarity
                        givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1"))),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .auctiontimestamp(1000L)
                        .build())));

        given(responseBidValidator.validate(any(), any(), any(), any())).willReturn(ValidationResult.success(
                singletonList("bid validation warning")));

        givenBidResponseCreator(singletonList(Bid.builder().build()));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipations();

        assertThat(auctionParticipations)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .hasSize(1);
        assertThat(auctionParticipations)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getErrors)
                .containsOnly(BidderError.invalidBid(
                        "BidId `bidId1` validation messages: Warning: bid validation warning"));
    }

    @Test
    public void shouldRejectBidIfCurrencyIsNotValid() {
        // given
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(singletonList(
                givenBidderBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(1.23)).build(),
                        "USDD"))));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                        // imp ids are not really used for matching, included them here for clarity
                        givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1"))),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .auctiontimestamp(1000L)
                        .build())));

        given(responseBidValidator.validate(any(), any(), any(), any()))
                .willReturn(ValidationResult.error("BidResponse currency is not valid: USDD"));

        final List<ExtBidderError> bidderErrors = singletonList(ExtBidderError.of(BidderError.Type.generic.getCode(),
                "BidResponse currency is not valid: USDD"));
        givenBidResponseCreator(singletonMap("bidder1", bidderErrors));

        // when
        final AuctionContext result = target.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        final BidResponse bidResponse = result.getBidResponse();
        final ExtBidResponse ext = bidResponse.getExt();
        assertThat(ext.getErrors()).hasSize(1)
                .containsOnly(entry("bidder1", bidderErrors));
        assertThat(bidResponse.getSeatbid())
                .extracting(SeatBid::getBid)
                .isEmpty();
    }

    @Test
    public void shouldCreateRequestsFromImpsReturnedByStoredResponseProcessor() {
        // given
        givenBidder(givenEmptySeatBid());

        final BidRequest bidRequest = givenBidRequest(asList(
                        givenImp(singletonMap("someBidder1", 1), builder -> builder
                                .id("impId1")
                                .banner(Banner.builder()
                                        .format(singletonList(Format.builder().w(400).h(300).build()))
                                        .build())),
                        givenImp(singletonMap("someBidder2", 1), builder -> builder
                                .id("impId2")
                                .banner(Banner.builder()
                                        .format(singletonList(Format.builder().w(400).h(300).build()))
                                        .build()))),
                builder -> builder.id("requestId").tmax(500L));

        given(storedResponseProcessor.getStoredResponseResult(any(), any()))
                .willReturn(Future.succeededFuture(StoredResponseResult
                        .of(singletonList(givenImp(singletonMap("someBidder1", 1), builder -> builder
                                .id("impId1")
                                .banner(Banner.builder()
                                        .format(singletonList(Format.builder().w(400).h(300).build()))
                                        .build()))), emptyList(), emptyMap())));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

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
                        .ext(mapper.valueToTree(ExtPrebid.of(null, 1)))
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

        final BidderBid bidderBid = BidderBid.of(Bid.builder().id("bidId1").price(ONE).build(), banner, "USD");
        final BidderSeatBid bidderSeatBid = BidderSeatBid.of(singletonList(bidderBid));
        given(storedResponseProcessor.mergeWithBidderResponses(any(), any(), any()))
                .willReturn(singletonList(
                        AuctionParticipation.builder()
                                .bidderResponse(BidderResponse.of("someBidder", bidderSeatBid, 100))
                                .build()));

        givenBidResponseCreator(singletonList(Bid.builder().id("bidId1").build()));

        // when
        final AuctionContext result = target.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(result.getBidResponse().getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getId)
                .containsOnly("bidId1");
    }

    @Test
    public void shouldReturnFailedFutureWhenStoredResponseProcessorGetStoredResultReturnsFailedFuture() {
        // given
        given(storedResponseProcessor.getStoredResponseResult(any(), any()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Error")));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                        givenImp(doubleMap("prebid", 0, "someBidder", 1), builder -> builder
                                .id("impId")
                                .banner(Banner.builder()
                                        .format(singletonList(Format.builder().w(400).h(300).build()))
                                        .build()))),
                builder -> builder.id("requestId").tmax(500L));

        // when
        final Future<?> result = target.holdAuction(givenRequestContext(bidRequest));

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
        final Future<?> result = target.holdAuction(givenRequestContext(bidRequest));

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class).hasMessage("Error");
    }

    @Test
    public void shouldHonorBuyeridFromRequestAndClearBuyerIdsFromUserExtPrebidIfContains() {
        // given
        givenBidder(givenEmptySeatBid());

        given(uidUpdater.updateUid(any(), any(), any())).willReturn(UpdateResult.updated("buyerid"));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.user(User.builder()
                        .buyeruid("buyerid")
                        .ext(ExtUser.builder()
                                .prebid(ExtUserPrebid.of(singletonMap("someBidder", "uidval")))
                                .build())
                        .build()));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final User capturedBidRequestUser = captureBidRequest().getUser();
        assertThat(capturedBidRequestUser).isEqualTo(User.builder()
                .buyeruid("buyerid")
                .build());
    }

    @Test
    public void shouldNotChangeGdprFromRequestWhenDeviceLmtIsOne() {
        // given
        givenBidder(givenEmptySeatBid());

        given(uidsCookie.uidFrom(anyString())).willReturn("buyeridFromCookie");

        final Regs regs = Regs.builder().build();
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.user(User.builder().build())
                        .device(Device.builder().lmt(1).build())
                        .regs(regs));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final Regs capturedRegs = captureBidRequest().getRegs();
        assertThat(capturedRegs).isSameAs(regs);
    }

    @Test
    public void shouldDeepCopyImpExtContextToEachImpressionAndNotRemoveDataForAllWhenDeprecatedOnlyOneBidder() {
        // given
        final ObjectNode impExt = mapper.createObjectNode()
                .<ObjectNode>set("prebid", mapper.createObjectNode()
                        .<ObjectNode>set("bidder", mapper.createObjectNode()
                                .put("someBidder", 1)
                                .put("deprecatedBidder", 2)))
                .set("context", mapper.createObjectNode()
                        .put("data", "data")
                        .put("otherField", "value"));
        final BidRequest bidRequest = givenBidRequest(singletonList(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(300).build()))
                                .build())
                        .ext(impExt)
                        .build()),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(singletonList("someBidder"), null))
                        .build())));
        given(httpBidderRequester.requestBids(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(singletonList(
                        givenBidderBid(Bid.builder().price(TEN).build())))));

        given(fpdResolver.resolveImpExt(any(), eq(true))).willReturn(mapper.createObjectNode()
                .set("context", mapper.createObjectNode()
                        .put("data", "data")
                        .put("otherField", "value")));
        given(fpdResolver.resolveImpExt(any(), eq(false))).willReturn(mapper.createObjectNode()
                .set("context", mapper.createObjectNode()
                        .put("otherField", "value")));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidderRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester, times(2))
                .requestBids(any(), bidderRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());
        assertThat(bidderRequestCaptor.getAllValues())
                .extracting(BidderRequest::getBidRequest)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(impExtNode -> impExtNode.get("context"))
                .containsOnly(
                        // data erased for deprecatedBidder
                        mapper.createObjectNode().put("otherField", "value"),
                        // data present for someBidder
                        mapper.createObjectNode().put("data", "data").put("otherField", "value"));
    }

    @Test
    public void shouldPassImpExtFieldsToEachImpression() {
        // given
        final ObjectNode impExt = mapper.createObjectNode()
                .<ObjectNode>set("prebid", mapper.createObjectNode()
                        .<ObjectNode>set("bidder", mapper.createObjectNode()
                                .put("someBidder", 1)))
                .put("all", "allValue");

        final BidRequest bidRequest = givenBidRequest(
                singletonList(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(300).build()))
                                .build()).ext(impExt).build()),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(singletonList("someBidder"), null))
                        .build())));
        given(httpBidderRequester.requestBids(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(singletonList(
                        givenBidderBid(Bid.builder().price(TEN).build())))));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidderRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(any(), bidderRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());
        assertThat(bidderRequestCaptor.getAllValues())
                .extracting(BidderRequest::getBidRequest)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(impExtNode -> impExtNode.get("all"))
                .containsOnly(new TextNode("allValue"));
    }

    @Test
    public void shouldPassImpExtSkadnToEachImpression() {
        // given
        final ObjectNode impExt = mapper.createObjectNode()
                .<ObjectNode>set("prebid", mapper.createObjectNode()
                        .<ObjectNode>set("bidder", mapper.createObjectNode()
                                .put("someBidder", 1)))
                .put("skadn", "skadnValue");
        final BidRequest bidRequest = givenBidRequest(
                singletonList(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(300).build()))
                                .build())
                        .ext(impExt)
                        .build()),
                identity());
        given(httpBidderRequester.requestBids(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(singletonList(
                        givenBidderBid(Bid.builder().price(TEN).build())))));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(any(), bidRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());
        assertThat(bidRequestCaptor.getAllValues())
                .extracting(BidderRequest::getBidRequest)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(impExtNode -> impExtNode.get("skadn"))
                .containsOnly(new TextNode("skadnValue"));
    }

    @Test
    public void shouldCleanRequestExtPrebidData() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(asList("someBidder", "should_be_removed"), null))
                        .auctiontimestamp(1000L)
                        .build())));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ExtRequest capturedRequest = captureBidRequest().getExt();
        assertThat(capturedRequest).isEqualTo(ExtRequest.of(ExtRequestPrebid.builder()
                .auctiontimestamp(1000L)
                .build()));
    }

    @Test
    public void shouldCleanRequestExtPrebidAliases() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("someBidder", "alias_should_stay"))
                        .auctiontimestamp(1000L)
                        .build())));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ExtRequest capturedRequest = captureBidRequest().getExt();
        assertThat(capturedRequest).isEqualTo(ExtRequest.of(ExtRequestPrebid.builder()
                .auctiontimestamp(1000L)
                .build()));
    }

    @Test
    public void shouldAddMultiBidInfoAboutRequestedBidderIgnoringCaseIfDataShouldNotBeSuppressed() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .multibid(singletonList(
                                ExtRequestPrebidMultiBid.of("SoMeBiDdeR", null, 3, "prefix")))
                        .build())));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ExtRequest extRequest = captureBidRequest().getExt();
        assertThat(extRequest)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getMultibid).asList()
                .containsExactly(ExtRequestPrebidMultiBid.of("someBidder", null, 3, "prefix"));
    }

    @Test
    public void shouldAddMultibidInfoOnlyAboutRequestedBidder() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .multibid(Collections.singletonList(
                                ExtRequestPrebidMultiBid.of(null, asList("someBidder", "anotherBidder"), 3, null)))
                        .build())));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ExtRequest extRequest = captureBidRequest().getExt();
        assertThat(extRequest)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getMultibid).asList()
                .containsExactly(ExtRequestPrebidMultiBid.of("someBidder", null, 3, null));
    }

    @Test
    public void shouldRemoveBidderParametersWithBiddersOtherThanBidderRequestBidder() {
        // given
        final ObjectNode requestBidderParams = mapper.createObjectNode()
                .set("someBidder", mapper.createObjectNode().put("key1", "value1"));
        requestBidderParams.set("anotherBidder", mapper.createObjectNode().put("key2", "value2"));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .bidderparams(requestBidderParams)
                        .auctiontimestamp(1000L)
                        .build())));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ExtRequest capturedRequest = captureBidRequest().getExt();
        assertThat(capturedRequest).isEqualTo(ExtRequest.of(ExtRequestPrebid.builder()
                .auctiontimestamp(1000L)
                .bidderparams(mapper.createObjectNode()
                        .set("someBidder", mapper.createObjectNode().put("key1", "value1")))
                .build()));
    }

    @Test
    public void shouldPassUserDataAndExtDataOnlyForAllowedBidder() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());
        givenBidder("missingBidder", bidder, givenEmptySeatBid());

        final ObjectNode dataNode = mapper.createObjectNode().put("data", "value");
        final Map<String, Integer> bidderToGdpr = doubleMap("someBidder", 1, "missingBidder", 0);
        final List<Eid> eids = singletonList(Eid.of("eId", emptyList(), null));
        final ExtUser extUser = ExtUser.builder().data(dataNode).build();
        final List<Data> data = singletonList(Data.builder().build());

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(bidderToGdpr),
                builder -> builder
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .auctiontimestamp(1000L)
                                .data(ExtRequestPrebidData.of(singletonList("someBidder"), null))
                                .build()))
                        .user(User.builder()
                                .keywords("keyword")
                                .gender("male")
                                .yob(133)
                                .geo(Geo.EMPTY)
                                .eids(eids)
                                .ext(extUser)
                                .data(data)
                                .build()));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidderRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester, times(2))
                .requestBids(any(), bidderRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());
        final List<BidderRequest> capturedBidRequests = bidderRequestCaptor.getAllValues();

        assertThat(capturedBidRequests)
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getUser)
                .extracting(
                        User::getKeywords,
                        User::getGender,
                        User::getYob,
                        User::getGeo,
                        User::getEids,
                        User::getExt,
                        User::getData)
                .containsOnly(
                        tuple("keyword", "male", 133, Geo.EMPTY, eids, extUser, data),
                        tuple("keyword", "male", 133, Geo.EMPTY, eids, null, null));
    }

    @Test
    public void shouldFilterUserExtEidsWhenBidderIsNotAllowedForSourceIgnoringCase() {
        testUserEidsPermissionFiltering(
                // given
                asList(
                        Eid.of("source1", null, null),
                        Eid.of("source2", null, null)),
                singletonList(ExtRequestPrebidDataEidPermissions.of("source1", singletonList("OtHeRbIdDeR"))),
                emptyMap(),
                // expected
                singletonList(Eid.of("source2", null, null))
        );
    }

    @Test
    public void shouldNotFilterUserExtEidsWhenEidsPermissionDoesNotContainSourceIgnoringCase() {
        testUserEidsPermissionFiltering(
                // given
                singletonList(Eid.of("source1", null, null)),
                singletonList(ExtRequestPrebidDataEidPermissions.of("source2", singletonList("OtHeRbIdDeR"))),
                emptyMap(),
                // expected
                singletonList(Eid.of("source1", null, null))
        );
    }

    @Test
    public void shouldNotFilterUserExtEidsWhenSourceAllowedForAllBiddersIgnoringCase() {
        testUserEidsPermissionFiltering(
                // given
                singletonList(Eid.of("source1", null, null)),
                singletonList(ExtRequestPrebidDataEidPermissions.of("source1", singletonList("*"))),
                emptyMap(),
                // expected
                singletonList(Eid.of("source1", null, null))
        );
    }

    @Test
    public void shouldNotFilterUserExtEidsWhenSourceAllowedForBidderIgnoringCase() {
        testUserEidsPermissionFiltering(
                // given
                singletonList(Eid.of("source1", null, null)),
                singletonList(ExtRequestPrebidDataEidPermissions.of("source1", singletonList("SoMeBiDdEr"))),
                emptyMap(),
                // expected
                singletonList(Eid.of("source1", null, null))
        );
    }

    @Test
    public void shouldFilterUserExtEidsWhenBidderIsNotAllowedForSourceAndSetNullIfNoEidsLeft() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());
        final Map<String, Integer> bidderToGdpr = singletonMap("someBidder", 1);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(bidderToGdpr),
                builder -> builder
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .data(ExtRequestPrebidData.of(null, singletonList(
                                        ExtRequestPrebidDataEidPermissions.of("source1",
                                                singletonList("otherBidder")))))
                                .build()))
                        .user(User.builder()
                                .eids(singletonList(Eid.of("source1", null, null)))
                                .ext(ExtUser.builder().data(mapper.createObjectNode()).build())
                                .build()));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidderRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(any(), bidderRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());
        final List<BidderRequest> capturedBidRequests = bidderRequestCaptor.getAllValues();
        assertThat(capturedBidRequests)
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getUser)
                .extracting(User::getEids)
                .element(0)
                .isNull();
    }

    @Test
    public void shouldFilterUserExtEidsWhenBidderPermissionsGivenToBidderAliasOnly() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());
        final Map<String, Integer> bidderToGdpr = singletonMap("someBidder", 1);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(bidderToGdpr),
                builder -> builder
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .aliases(singletonMap("someBidder", "someBidderAlias"))
                                .data(ExtRequestPrebidData.of(null, singletonList(
                                        ExtRequestPrebidDataEidPermissions.of("source1",
                                                singletonList("someBidderAlias")))))
                                .build()))
                        .user(User.builder()
                                .eids(singletonList(Eid.of("source1", null, null)))
                                .ext(ExtUser.builder().data(mapper.createObjectNode()).build())
                                .build()));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidderRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(any(), bidderRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());
        final List<BidderRequest> capturedBidRequests = bidderRequestCaptor.getAllValues();
        assertThat(capturedBidRequests)
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getUser)
                .extracting(User::getEids)
                .element(0)
                .isNull();
    }

    @Test
    public void shouldFilterUserExtEidsWhenPermissionsGivenToBidderButNotForAlias() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidderAlias", bidder, givenEmptySeatBid());
        final Map<String, Integer> bidderToGdpr = singletonMap("someBidderAlias", 1);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(bidderToGdpr),
                builder -> builder
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .aliases(singletonMap("someBidder", "someBidderAlias"))
                                .data(ExtRequestPrebidData.of(null, singletonList(
                                        ExtRequestPrebidDataEidPermissions.of("source1",
                                                singletonList("someBidder")))))
                                .build()))
                        .user(User.builder()
                                .eids(singletonList(Eid.of("source1", null, null)))
                                .ext(ExtUser.builder().data(mapper.createObjectNode()).build())
                                .build()));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidderRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(any(), bidderRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());
        final List<BidderRequest> capturedBidRequests = bidderRequestCaptor.getAllValues();
        assertThat(capturedBidRequests)
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getUser)
                .extracting(User::getEids)
                .element(0)
                .isNull();
    }

    @Test
    public void shouldNotCleanRequestExtPrebidDataWhenFpdAllowedAndPrebidIsNotNullIgnoringCase() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());

        final ObjectNode dataNode = mapper.createObjectNode().put("data", "value");
        final Map<String, Integer> bidderToGdpr = singletonMap("someBidder", 1);
        final ExtUser extUser = ExtUser.builder().prebid(ExtUserPrebid.of(emptyMap())).data(dataNode).build();

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(bidderToGdpr),
                builder -> builder
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .auctiontimestamp(1000L)
                                .data(ExtRequestPrebidData.of(singletonList("SoMeBiDdEr"), null))
                                .build()))
                        .user(User.builder()
                                .ext(extUser)
                                .build()));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidderRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(any(), bidderRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());
        final List<BidderRequest> capturedBidRequests = bidderRequestCaptor.getAllValues();
        assertThat(capturedBidRequests)
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getUser)
                .extracting(User::getExt)
                .containsOnly(ExtUser.builder().data(dataNode).build());
    }

    @Test
    public void shouldMaskUserExtIfDataBiddersListIsEmpty() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());
        givenBidder("missingBidder", bidder, givenEmptySeatBid());

        final ObjectNode dataNode = mapper.createObjectNode().put("data", "value");
        final Map<String, Integer> bidderToGdpr = doubleMap("someBidder", 1, "missingBidder", 0);
        final List<Eid> eids = singletonList(Eid.of("eId", emptyList(), null));
        final ExtUser extUser = ExtUser.builder().data(dataNode).build();

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(bidderToGdpr),
                builder -> builder
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .data(ExtRequestPrebidData.of(emptyList(), null)).build()))
                        .user(User.builder()
                                .keywords("keyword")
                                .gender("male")
                                .yob(133)
                                .geo(Geo.EMPTY)
                                .eids(eids)
                                .ext(extUser)
                                .build()));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidderRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester, times(2))
                .requestBids(any(), bidderRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());
        final List<BidderRequest> capturedBidRequests = bidderRequestCaptor.getAllValues();

        assertThat(capturedBidRequests)
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getUser)
                .extracting(User::getKeywords, User::getGender, User::getYob, User::getGeo, User::getEids, User::getExt)
                .containsOnly(
                        tuple("keyword", "male", 133, Geo.EMPTY, eids, null),
                        tuple("keyword", "male", 133, Geo.EMPTY, eids, null));
    }

    @Test
    public void shouldNoMaskUserExtIfDataBiddersListIsNull() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());
        givenBidder("missingBidder", bidder, givenEmptySeatBid());

        final ObjectNode dataNode = mapper.createObjectNode().put("data", "value");
        final Map<String, Integer> bidderToGdpr = doubleMap("someBidder", 1, "missingBidder", 0);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(bidderToGdpr),
                builder -> builder
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .auctiontimestamp(1000L)
                                .data(ExtRequestPrebidData.of(null, null)).build()))
                        .user(User.builder()
                                .keywords("keyword")
                                .gender("male")
                                .yob(133)
                                .geo(Geo.EMPTY)
                                .ext(ExtUser.builder().data(dataNode).build())
                                .build()));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester, times(2))
                .requestBids(any(), bidRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());
        final List<BidderRequest> capturedBidRequests = bidRequestCaptor.getAllValues();

        assertThat(capturedBidRequests)
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getUser)
                .extracting(User::getKeywords, User::getGender, User::getYob, User::getGeo, User::getExt)
                .containsOnly(
                        tuple("keyword", "male", 133, Geo.EMPTY,
                                ExtUser.builder().data(dataNode).build()),
                        tuple("keyword", "male", 133, Geo.EMPTY,
                                ExtUser.builder().data(dataNode).build()));
    }

    @Test
    public void shouldPassSiteContentDataAndExtDataOnlyForAllowedBidder() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());
        givenBidder("missingBidder", bidder, givenEmptySeatBid());

        final ObjectNode dataNode = mapper.createObjectNode().put("data", "value");
        final Map<String, Integer> bidderToGdpr = doubleMap("someBidder", 1, "missingBidder", 0);
        final Content content = Content.builder()
                .data(singletonList(Data.builder().build()))
                .album("album")
                .build();

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(bidderToGdpr),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .auctiontimestamp(1000L)
                                .data(ExtRequestPrebidData.of(singletonList("someBidder"), null)).build()))
                        .site(Site.builder()
                                .keywords("keyword")
                                .search("search")
                                .ext(ExtSite.of(0, dataNode))
                                .content(content)
                                .build()));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidderRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester, times(2))
                .requestBids(any(), bidderRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());
        final List<BidderRequest> capturedBidRequests = bidderRequestCaptor.getAllValues();

        assertThat(capturedBidRequests)
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getSite)
                .extracting(Site::getKeywords, Site::getSearch, Site::getExt, Site::getContent)
                .containsOnly(
                        tuple(
                                "keyword",
                                "search",
                                ExtSite.of(0, dataNode),
                                content),
                        tuple(
                                "keyword",
                                "search",
                                ExtSite.of(0, null),
                                Content.builder()
                                        .album("album")
                                        .build()));
    }

    @Test
    public void shouldPassDoohContentDataAndExtDataOnlyForAllowedBidder() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());
        givenBidder("missingBidder", bidder, givenEmptySeatBid());

        final ObjectNode dataNode = mapper.createObjectNode().put("data", "value");
        final Map<String, Integer> bidderToGdpr = doubleMap("someBidder", 1, "missingBidder", 0);
        final Content content = Content.builder()
                .data(singletonList(Data.builder().build()))
                .album("album")
                .build();

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(bidderToGdpr),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .auctiontimestamp(1000L)
                                .data(ExtRequestPrebidData.of(singletonList("someBidder"), null)).build()))
                        .dooh(Dooh.builder()
                                .keywords("keyword")
                                .venuetype(List.of("venuetype"))
                                .ext(ExtDooh.of(dataNode))
                                .content(content)
                                .build()));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidderRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester, times(2))
                .requestBids(any(), bidderRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());
        final List<BidderRequest> capturedBidRequests = bidderRequestCaptor.getAllValues();

        assertThat(capturedBidRequests)
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getDooh)
                .extracting(Dooh::getKeywords, Dooh::getVenuetype, Dooh::getExt, Dooh::getContent)
                .containsOnly(
                        tuple(
                                "keyword",
                                List.of("venuetype"),
                                ExtDooh.of(dataNode),
                                content),
                        tuple(
                                "keyword",
                                List.of("venuetype"),
                                null,
                                Content.builder().album("album").build()));
    }

    @Test
    public void shouldPassAppContentDataAndExtDataOnlyForAllowedBidder() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());
        givenBidder("missingBidder", bidder, givenEmptySeatBid());

        final ObjectNode dataNode = mapper.createObjectNode().put("data", "value");
        final Map<String, Integer> bidderToGdpr = doubleMap("someBidder", 1, "missingBidder", 0);
        final Content content = Content.builder()
                .data(singletonList(Data.builder().build()))
                .album("album")
                .build();

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(bidderToGdpr),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .auctiontimestamp(1000L)
                                .data(ExtRequestPrebidData.of(singletonList("someBidder"), null)).build()))
                        .app(App.builder()
                                .keywords("keyword")
                                .storeurl("storeurl")
                                .ext(ExtApp.of(ExtAppPrebid.of("source", "version"), dataNode))
                                .content(content)
                                .build()));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidderRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester, times(2))
                .requestBids(any(), bidderRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());
        final List<BidderRequest> capturedBidRequests = bidderRequestCaptor.getAllValues();

        assertThat(capturedBidRequests)
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getApp)
                .extracting(App::getKeywords, App::getStoreurl, App::getExt, App::getContent)
                .containsOnly(
                        tuple(
                                "keyword",
                                "storeurl",
                                ExtApp.of(ExtAppPrebid.of("source", "version"), dataNode),
                                content),
                        tuple(
                                "keyword",
                                "storeurl",
                                ExtApp.of(ExtAppPrebid.of("source", "version"), null),
                                Content.builder().album("album").build()));
    }

    @Test
    public void shouldNoMaskPassAppExtAndKeywordsWhenDataBiddersListIsNull() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());
        givenBidder("missingBidder", bidder, givenEmptySeatBid());

        final ObjectNode dataNode = mapper.createObjectNode().put("data", "value");
        final Map<String, Integer> bidderToGdpr = doubleMap("someBidder", 1, "missingBidder", 0);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(bidderToGdpr),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .data(ExtRequestPrebidData.of(null, null))
                                .auctiontimestamp(1000L).build()))
                        .app(App.builder()
                                .keywords("keyword")
                                .ext(ExtApp.of(null, dataNode))
                                .build()));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidderRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester, times(2))
                .requestBids(any(), bidderRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());
        final List<BidderRequest> capturedBidRequests = bidderRequestCaptor.getAllValues();

        assertThat(capturedBidRequests)
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getApp)
                .extracting(App::getExt, App::getKeywords)
                .containsOnly(
                        tuple(ExtApp.of(null, dataNode), "keyword"),
                        tuple(ExtApp.of(null, dataNode), "keyword"));
    }

    @Test
    public void shouldNoMaskPassDoohExtAndKeywordsWhenDataBiddersListIsNull() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());
        givenBidder("missingBidder", bidder, givenEmptySeatBid());

        final ObjectNode dataNode = mapper.createObjectNode().put("data", "value");
        final Map<String, Integer> bidderToGdpr = doubleMap("someBidder", 1, "missingBidder", 0);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(bidderToGdpr),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .data(ExtRequestPrebidData.of(null, null))
                                .auctiontimestamp(1000L).build()))
                        .dooh(Dooh.builder()
                                .keywords("keyword")
                                .ext(ExtDooh.of(dataNode))
                                .build()));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidderRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester, times(2))
                .requestBids(any(), bidderRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());
        final List<BidderRequest> capturedBidRequests = bidderRequestCaptor.getAllValues();

        assertThat(capturedBidRequests)
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getDooh)
                .extracting(Dooh::getExt, Dooh::getKeywords)
                .containsOnly(
                        tuple(ExtDooh.of(dataNode), "keyword"),
                        tuple(ExtDooh.of(dataNode), "keyword"));
    }

    @Test
    public void shouldUseConcreteOverGeneralSiteWithExtPrebidBidderConfigIgnoringCase() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());

        final ObjectNode siteWithPage = mapper.valueToTree(Site.builder().page("testPage").build());
        final ExtBidderConfig extBidderConfig = ExtBidderConfig.of(
                null, ExtBidderConfigOrtb.of(siteWithPage, null, null, null));
        final ExtRequestPrebidBidderConfig concreteFpdConfig = ExtRequestPrebidBidderConfig.of(
                singletonList("SoMeBiDdEr"), extBidderConfig);
        final ObjectNode siteWithDomain = mapper.valueToTree(Site.builder().domain("notUsed").build());
        final ExtBidderConfig allExtBidderConfig = ExtBidderConfig.of(
                null, ExtBidderConfigOrtb.of(siteWithDomain, null, null, null));
        final ExtRequestPrebidBidderConfig allFpdConfig = ExtRequestPrebidBidderConfig.of(singletonList("*"),
                allExtBidderConfig);

        final Site requestSite = Site.builder().id("siteId").page("erased").keywords("keyword").build();
        final ExtRequestPrebid extRequestPrebid = ExtRequestPrebid.builder()
                .bidderconfig(asList(allFpdConfig, concreteFpdConfig))
                .build();
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.site(requestSite).ext(ExtRequest.of(extRequestPrebid)));

        final Site mergedSite = Site.builder()
                .id("siteId")
                .page("testPage")
                .keywords("keyword")
                .build();

        given(fpdResolver.resolveSite(any(), any())).willReturn(mergedSite);

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidderRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(any(), bidderRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());
        final List<BidderRequest> capturedBidRequests = bidderRequestCaptor.getAllValues();

        assertThat(capturedBidRequests)
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getSite)
                .containsOnly(mergedSite);
    }

    @Test
    public void shouldUseConcreteOverGeneralDoohWithExtPrebidBidderConfig() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());

        final ObjectNode doohWithVenueType = mapper.valueToTree(Dooh.builder().venuetype(List.of("venuetype")).build());
        final ExtBidderConfig extBidderConfig = ExtBidderConfig.of(
                null, ExtBidderConfigOrtb.of(null, null, doohWithVenueType, null));
        final ExtRequestPrebidBidderConfig concreteFpdConfig = ExtRequestPrebidBidderConfig.of(
                singletonList("someBidder"), extBidderConfig);
        final ObjectNode doohWithDomain = mapper.valueToTree(Dooh.builder().domain("notUsed").build());
        final ExtBidderConfig allExtBidderConfig = ExtBidderConfig.of(
                null, ExtBidderConfigOrtb.of(null, null, doohWithDomain, null));
        final ExtRequestPrebidBidderConfig allFpdConfig = ExtRequestPrebidBidderConfig.of(
                singletonList("*"),
                allExtBidderConfig);

        final Dooh requestDooh = Dooh.builder().id("doohId").venuetype(List.of("erased")).keywords("keyword").build();
        final ExtRequestPrebid extRequestPrebid = ExtRequestPrebid.builder()
                .bidderconfig(asList(allFpdConfig, concreteFpdConfig))
                .build();
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.dooh(requestDooh).ext(ExtRequest.of(extRequestPrebid)));

        final Dooh mergedDooh = Dooh.builder()
                .id("doohId")
                .venuetype(List.of("venuetype"))
                .keywords("keyword")
                .build();

        given(fpdResolver.resolveDooh(any(), any())).willReturn(mergedDooh);

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidderRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(any(), bidderRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());
        final List<BidderRequest> capturedBidRequests = bidderRequestCaptor.getAllValues();

        assertThat(capturedBidRequests)
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getDooh)
                .containsOnly(mergedDooh);
    }

    @Test
    public void shouldUseConcreteOverGeneralAppWithExtPrebidBidderConfigIgnoringCase() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());

        final Publisher publisherWithId = Publisher.builder().id("testId").build();
        final ObjectNode appWithPublisherId = mapper.valueToTree(App.builder().publisher(publisherWithId).build());
        final ExtBidderConfig extBidderConfig = ExtBidderConfig.of(
                null, ExtBidderConfigOrtb.of(null, appWithPublisherId, null, null));
        final ExtRequestPrebidBidderConfig concreteFpdConfig = ExtRequestPrebidBidderConfig.of(
                singletonList("SoMeBiDdEr"), extBidderConfig);

        final Publisher publisherWithIdAndDomain = Publisher.builder().id("notUsed").domain("notUsed").build();
        final ObjectNode appWithUpdatedPublisher = mapper.valueToTree(
                App.builder().publisher(publisherWithIdAndDomain).build());
        final ExtBidderConfig allExtBidderConfig = ExtBidderConfig.of(
                null, ExtBidderConfigOrtb.of(null, appWithUpdatedPublisher, null, null));
        final ExtRequestPrebidBidderConfig allFpdConfig = ExtRequestPrebidBidderConfig.of(singletonList("*"),
                allExtBidderConfig);

        final App requestApp = App.builder().publisher(Publisher.builder().build()).build();

        final ExtRequestPrebid extRequestPrebid = ExtRequestPrebid.builder()
                .bidderconfig(asList(allFpdConfig, concreteFpdConfig))
                .build();
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.app(requestApp).ext(ExtRequest.of(extRequestPrebid)));
        final App mergedApp = App.builder()
                .publisher(Publisher.builder().id("testId").build())
                .build();

        given(fpdResolver.resolveApp(any(), any())).willReturn(mergedApp);

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidderRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(any(), bidderRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());
        final List<BidderRequest> capturedBidRequests = bidderRequestCaptor.getAllValues();

        assertThat(capturedBidRequests)
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getApp)
                .containsOnly(mergedApp);
    }

    @Test
    public void shouldUseConcreteOverGeneralUserWithExtPrebidBidderConfig() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());
        final ObjectNode bidderConfigUser = mapper.valueToTree(User.builder().id("userFromConfig").build());
        final ExtBidderConfig extBidderConfig = ExtBidderConfig.of(
                null, ExtBidderConfigOrtb.of(null, null, null, bidderConfigUser));
        final ExtRequestPrebidBidderConfig concreteFpdConfig = ExtRequestPrebidBidderConfig.of(
                singletonList("SomMeBiDdEr"), extBidderConfig);

        final ObjectNode emptyUser = mapper.valueToTree(User.builder().build());
        final ExtBidderConfig allExtBidderConfig = ExtBidderConfig.of(
                null, ExtBidderConfigOrtb.of(null, null, null, emptyUser));
        final ExtRequestPrebidBidderConfig allFpdConfig = ExtRequestPrebidBidderConfig.of(singletonList("*"),
                allExtBidderConfig);
        final User requestUser = User.builder().id("erased").buyeruid("testBuyerId").build();

        final ExtRequestPrebid extRequestPrebid = ExtRequestPrebid.builder()
                .bidderconfig(asList(allFpdConfig, concreteFpdConfig))
                .build();
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.user(requestUser).ext(ExtRequest.of(extRequestPrebid)));

        final User mergedUser = User.builder().id("userFromConfig").buyeruid("testBuyerId").build();

        given(fpdResolver.resolveUser(any(), any())).willReturn(mergedUser);

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidderRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(any(), bidderRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());
        final List<BidderRequest> capturedBidRequests = bidderRequestCaptor.getAllValues();

        assertThat(capturedBidRequests)
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getUser)
                .containsOnly(mergedUser);
    }

    @Test
    public void shouldAddBuyeridToUserFromRequest() {
        // given
        givenBidder(givenEmptySeatBid());
        given(uidUpdater.updateUid(any(), any(), any()))
                .willReturn(UpdateResult.updated("buyerid"));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.user(User.builder().id("userId").build()));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final User capturedUser = captureBidRequest().getUser();
        assertThat(capturedUser).isEqualTo(User.builder().id("userId").buyeruid("buyerid").build());
    }

    @Test
    public void shouldCreateUserIfMissingInRequestAndBuyeridPresentForBidder() {
        // given
        givenBidder(givenEmptySeatBid());

        given(uidUpdater.updateUid(eq("someBidder"), any(), any()))
                .willReturn(UpdateResult.updated("buyerid"));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final User capturedUser = captureBidRequest().getUser();
        assertThat(capturedUser).isEqualTo(User.builder().buyeruid("buyerid").build());
    }

    @Test
    public void holdAuctionShouldRemoveDoohWhenFpdProvidesSiteButDoohIsAlreadyInBidRequest() {
        // given
        givenBidder(givenEmptySeatBid());
        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder.dooh(Dooh.builder().build()));

        given(fpdResolver.resolveSite(any(), any())).willReturn(Site.builder().build());

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final BidRequest captureBidRequest = captureBidRequest();
        assertThat(captureBidRequest.getDooh()).isNotNull();
        assertThat(captureBidRequest.getSite()).isNull();

        verify(metrics).updateAlertsMetrics(MetricName.general);
    }

    @Test
    public void holdAuctionShouldRemoveSiteAndDoohWhenSiteAppAndDoohArePresentInBidRequest() {
        // given
        givenBidder(givenEmptySeatBid());
        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .site(Site.builder().build())
                        .app(App.builder().build())
                        .dooh(Dooh.builder().build()));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final BidRequest captureBidRequest = captureBidRequest();
        assertThat(captureBidRequest.getDooh()).isNull();
        assertThat(captureBidRequest.getSite()).isNull();
        assertThat(captureBidRequest.getApp()).isNotNull();

        verify(metrics).updateAlertsMetrics(MetricName.general);
    }

    @Test
    public void holdAuctionShouldRemoveSiteWhenFpdProvidesAppButSiteIsAlreadyInBidRequest() {
        // given
        givenBidder(givenEmptySeatBid());
        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder.site(Site.builder().build()));

        given(fpdResolver.resolveApp(any(), any())).willReturn(App.builder().build());

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final BidRequest captureBidRequest = captureBidRequest();
        assertThat(captureBidRequest.getSite()).isNull();
        assertThat(captureBidRequest.getApp()).isNotNull();

        verify(metrics).updateAlertsMetrics(MetricName.general);
    }

    @Test
    public void holdAuctionShouldFailWhenFpdProvidesSiteButDoohIsAlreadyInBidRequestAndStrictValidationEnabled() {
        // given
        givenTarget(true);
        givenBidder(givenEmptySeatBid());
        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder.dooh(Dooh.builder().build()));

        given(fpdResolver.resolveSite(any(), any())).willReturn(Site.builder().build());

        // when
        final Throwable cause = target.holdAuction(givenRequestContext(bidRequest)).cause();

        assertThat(cause.getMessage()).isEqualTo(
                "dooh and site are present, but no more than one of site or app or dooh can be defined");

        verify(metrics).updateAlertsMetrics(MetricName.general);
    }

    @Test
    public void holdAuctionShouldFailWhenSiteAppAndDoohArePresentInBidRequestAndStrictValidationEnabled() {
        // given
        givenTarget(true);
        givenBidder(givenEmptySeatBid());
        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .site(Site.builder().build())
                        .app(App.builder().build())
                        .dooh(Dooh.builder().build()));

        // when
        final Throwable cause = target.holdAuction(givenRequestContext(bidRequest)).cause();

        //then
        assertThat(cause.getMessage()).isEqualTo(
                "app and dooh and site are present, but no more than one of site or app or dooh can be defined");
        verify(metrics).updateAlertsMetrics(MetricName.general);
    }

    @Test
    public void shouldReturnBidsWithUpdatedPriceCurrencyConversion() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenSeatBid(singletonList(
                givenBidderBid(Bid.builder().impid("impId").price(BigDecimal.valueOf(2.0)).build()))));

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                identity());

        final BigDecimal updatedPrice = BigDecimal.valueOf(5.0);
        given(currencyService.convertCurrency(any(), any(), any(), any())).willReturn(updatedPrice);

        givenBidResponseCreator(singletonList(Bid.builder().price(updatedPrice).build()));

        // when
        final AuctionContext result = target.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(result.getBidResponse().getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getPrice).containsExactly(updatedPrice);
    }

    @Test
    public void shouldApplyStoredBidResponseAdjustments() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenSeatBid(singletonList(
                givenBidderBid(Bid.builder().impid("impId").price(BigDecimal.ONE).build()))));

        final BidRequest bidRequest = givenBidRequest(
                singletonList(givenImp(singletonMap("bidder", 2), identity())), identity());

        given(currencyService.convertCurrency(any(), any(), any(), any()))
                .willReturn(TEN);

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        verify(storedResponseProcessor).updateStoredBidResponse(any());
    }

    @Test
    public void shouldReturnSameBidPriceIfNoChangesAppliedToBidPrice() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenSeatBid(singletonList(
                givenBidderBid(Bid.builder().impid("impId").price(BigDecimal.ONE).build()))));

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                identity());

        // returns the same price as in argument
        given(currencyService.convertCurrency(any(), any(), any(), any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        final AuctionContext result = target.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(result.getBidResponse().getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getPrice).containsExactly(BigDecimal.ONE);
    }

    @Test
    public void shouldDropBidsWithInvalidPriceAndAddDebugWarnings() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        final List<Bid> bids = List.of(
                Bid.builder().id("valid_bid").impid("impId").price(BigDecimal.valueOf(2.0)).build(),
                Bid.builder().id("invalid_bid_1").impid("impId").price(null).build(),
                Bid.builder().id("invalid_bid_2").impid("impId").price(BigDecimal.ZERO).build(),
                Bid.builder().id("invalid_bid_3").impid("impId").price(BigDecimal.valueOf(-0.01)).build());
        final BidderSeatBid seatBid = givenSeatBid(bids.stream().map(ExchangeServiceTest::givenBidderBid).toList());

        givenBidder("bidder", bidder, seatBid);

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                identity());
        final AuctionContext givenContext = givenRequestContext(bidRequest);

        // when
        final AuctionContext result = target.holdAuction(givenContext).result();

        // then
        assertThat(result.getBidResponse().getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1);
        assertThat(givenContext.getDebugWarnings())
                .containsExactlyInAnyOrder(
                        "Dropped bid 'invalid_bid_1'. Does not contain a positive (or zero if there is a deal) 'price'",
                        "Dropped bid 'invalid_bid_2'. Does not contain a positive (or zero if there is a deal) 'price'",
                        "Dropped bid 'invalid_bid_3'. Does not contain a positive (or zero if there is a deal) 'price'"
                );
        verify(metrics, times(3)).updateAdapterRequestErrorMetric("bidder", MetricName.unknown_error);
    }

    @Test
    public void shouldDropBidIfPrebidExceptionWasThrownDuringCurrencyConversion() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenSeatBid(singletonList(
                givenBidderBid(Bid.builder().price(BigDecimal.valueOf(2.0)).build(), "CUR"))));

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                identity());

        given(currencyService.convertCurrency(any(), any(), any(), any()))
                .willThrow(new PreBidException("Unable to convert bid currency CUR to desired ad server currency USD"));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipations();
        assertThat(auctionParticipations).hasSize(1);

        final BidderError expectedError =
                BidderError.generic("Unable to convert bid currency CUR to desired ad server currency USD");
        final BidderSeatBid firstSeatBid = auctionParticipations.get(0).getBidderResponse().getSeatBid();
        assertThat(firstSeatBid.getBids()).isEmpty();
        assertThat(firstSeatBid.getErrors()).containsOnly(expectedError);
    }

    @Test
    public void shouldUpdateBidPriceWithCurrencyConversionAndPriceAdjustmentFactor() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenSeatBid(singletonList(
                givenBidderBid(Bid.builder().impid("impId").price(BigDecimal.valueOf(2.0)).build()))));

        final ExtRequestBidAdjustmentFactors givenAdjustments = ExtRequestBidAdjustmentFactors.builder().build();
        givenAdjustments.addFactor("bidder", TEN);

        given(bidAdjustmentFactorResolver.resolve(ImpMediaType.banner, givenAdjustments, "bidder"))
                .willReturn(TEN);

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(emptyMap())
                        .bidadjustmentfactors(givenAdjustments)
                        .auctiontimestamp(1000L)
                        .build())));

        given(currencyService.convertCurrency(any(), any(), any(), any()))
                .willReturn(TEN);

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipations();
        assertThat(auctionParticipations).hasSize(1);

        final BigDecimal updatedPrice = BigDecimal.valueOf(100);
        final BidderSeatBid firstSeatBid = auctionParticipations.get(0).getBidderResponse().getSeatBid();
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
        givenBidder("bidder", mock(Bidder.class), givenSeatBid(asList(
                givenBidderBid(Bid.builder().impid("impId1").price(firstBidderPrice).build(), "CUR1"),
                givenBidderBid(Bid.builder().impid("impId2").price(secondBidderPrice).build(), "CUR2"))));

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                identity());

        final BigDecimal updatedPrice = BigDecimal.valueOf(10.0);
        given(currencyService.convertCurrency(any(), any(), any(), any())).willReturn(updatedPrice)
                .willThrow(
                        new PreBidException("Unable to convert bid currency CUR2 to desired ad server currency USD"));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<AuctionContext> contextArgumentCaptor = ArgumentCaptor.forClass(AuctionContext.class);
        verify(bidResponseCreator).create(contextArgumentCaptor.capture(), any(), any());
        verify(currencyService).convertCurrency(eq(firstBidderPrice), eq(bidRequest), eq("CUR1"), any());
        verify(currencyService).convertCurrency(eq(secondBidderPrice), eq(bidRequest), eq("CUR2"), any());

        final List<AuctionParticipation> auctionParticipations =
                contextArgumentCaptor.getValue().getAuctionParticipations();
        assertThat(auctionParticipations).hasSize(1);

        final ObjectNode expectedBidExt = mapper.createObjectNode();
        expectedBidExt.put("origbidcpm", new BigDecimal("2.0"));
        expectedBidExt.put("origbidcur", "CUR1");
        final Bid expectedBid = Bid.builder().impid("impId1").price(updatedPrice).ext(expectedBidExt).build();
        final BidderBid expectedBidderBid = BidderBid.of(expectedBid, banner, "CUR1");
        final BidderError expectedError =
                BidderError.generic("Unable to convert bid currency CUR2 to desired ad server currency USD");

        final BidderSeatBid firstSeatBid = auctionParticipations.get(0).getBidderResponse().getSeatBid();
        assertThat(firstSeatBid.getBids()).containsOnly(expectedBidderBid);
        assertThat(firstSeatBid.getErrors()).containsOnly(expectedError);
    }

    @Test
    public void shouldRespondWithOneBidAndErrorWhenBidResponseContainsOneUnsupportedCurrency() {
        // given
        final BigDecimal firstBidderPrice = BigDecimal.valueOf(2.0);
        final BigDecimal secondBidderPrice = BigDecimal.valueOf(10.0);
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(singletonList(
                givenBidderBid(Bid.builder().impid("impId1").price(firstBidderPrice).build(), "USD"))));
        givenBidder("bidder2", mock(Bidder.class), givenSeatBid(singletonList(
                givenBidderBid(Bid.builder().impid("impId2").price(BigDecimal.valueOf(10.0)).build(), "CUR"))));

        final BidRequest bidRequest = BidRequest.builder().cur(singletonList("BAD"))
                .imp(singletonList(givenImp(doubleMap("bidder1", 2, "bidder2", 3),
                        identity()))).build();

        final BigDecimal updatedPrice = BigDecimal.valueOf(20);
        given(currencyService.convertCurrency(any(), any(), any(), any())).willReturn(updatedPrice);
        given(currencyService.convertCurrency(any(), any(), eq("CUR"), eq("BAD")))
                .willThrow(new PreBidException("Unable to convert bid currency CUR to desired ad server currency BAD"));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<AuctionContext> contextArgumentCaptor = ArgumentCaptor.forClass(AuctionContext.class);
        verify(bidResponseCreator).create(contextArgumentCaptor.capture(), any(), any());
        verify(currencyService).convertCurrency(eq(firstBidderPrice), eq(bidRequest), eq("USD"), eq("BAD"));
        verify(currencyService).convertCurrency(eq(secondBidderPrice), eq(bidRequest), eq("CUR"), eq("BAD"));

        final List<AuctionParticipation> auctionParticipations =
                contextArgumentCaptor.getValue().getAuctionParticipations();
        assertThat(auctionParticipations).hasSize(2);

        final ObjectNode expectedBidExt = mapper.createObjectNode();
        expectedBidExt.put("origbidcpm", new BigDecimal("2.0"));
        expectedBidExt.put("origbidcur", "USD");
        final Bid expectedBid = Bid.builder().impid("impId1").price(updatedPrice).ext(expectedBidExt).build();
        final BidderBid expectedBidderBid = BidderBid.of(expectedBid, banner, "USD");
        assertThat(auctionParticipations)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .containsOnly(expectedBidderBid);

        final BidderError expectedError =
                BidderError.generic("Unable to convert bid currency CUR to desired ad server currency BAD");
        assertThat(auctionParticipations)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getErrors)
                .containsOnly(expectedError);
    }

    @Test
    public void shouldUpdateBidPriceWithCurrencyConversionAndAddErrorAboutMultipleCurrency() {
        // given
        final BigDecimal bidderPrice = BigDecimal.valueOf(2.0);
        givenBidder("bidder", mock(Bidder.class), givenSeatBid(singletonList(
                givenBidderBid(Bid.builder().impid("impId").price(bidderPrice).build(), "USD"))));

        final BidRequest bidRequest = givenBidRequest(
                singletonList(givenImp(singletonMap("bidder", 2), identity())),
                builder -> builder.cur(asList("CUR1", "CUR2", "CUR2")));

        final BigDecimal updatedPrice = BigDecimal.valueOf(10.0);
        given(currencyService.convertCurrency(any(), any(), any(), any())).willReturn(updatedPrice);

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<AuctionContext> contextArgumentCaptor = ArgumentCaptor.forClass(AuctionContext.class);
        verify(bidResponseCreator).create(contextArgumentCaptor.capture(), any(), any());
        verify(currencyService).convertCurrency(eq(bidderPrice), eq(bidRequest), eq("USD"), eq("CUR1"));

        final List<AuctionParticipation> auctionParticipations =
                contextArgumentCaptor.getValue().getAuctionParticipations();
        assertThat(auctionParticipations).hasSize(1);

        final BidderError expectedError = BidderError.badInput("Cur parameter contains more than one currency."
                + " CUR1 will be used");
        final BidderSeatBid firstSeatBid = auctionParticipations.get(0).getBidderResponse().getSeatBid();
        assertThat(firstSeatBid.getBids())
                .extracting(BidderBid::getBid)
                .flatExtracting(Bid::getPrice)
                .containsOnly(updatedPrice);
        assertThat(firstSeatBid.getErrors()).containsOnly(expectedError);
    }

    @Test
    public void shouldUpdateBidPriceWithCurrencyConversionForMultipleBid() {
        // given
        final BigDecimal bidder1Price = BigDecimal.valueOf(1.5);
        final BigDecimal bidder2Price = BigDecimal.valueOf(2);
        final BigDecimal bidder3Price = BigDecimal.valueOf(3);
        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(singletonList(
                givenBidderBid(Bid.builder().impid("impId1").price(bidder1Price).build(), "EUR"))));
        givenBidder("bidder2", mock(Bidder.class), givenSeatBid(singletonList(
                givenBidderBid(Bid.builder().impid("impId2").price(bidder2Price).build(), "GBP"))));
        givenBidder("bidder3", mock(Bidder.class), givenSeatBid(singletonList(
                givenBidderBid(Bid.builder().impid("impId3").price(bidder3Price).build(), "USD"))));

        final Map<String, Integer> impBidders = new HashMap<>();
        impBidders.put("bidder1", 1);
        impBidders.put("bidder2", 2);
        impBidders.put("bidder3", 3);
        final BidRequest bidRequest = givenBidRequest(
                singletonList(givenImp(impBidders, identity())), builder -> builder.cur(singletonList("USD")));

        final BigDecimal updatedPrice = BigDecimal.valueOf(10.0);
        given(currencyService.convertCurrency(any(), any(), any(), any())).willReturn(updatedPrice);
        given(currencyService.convertCurrency(any(), any(), eq("USD"), any())).willReturn(bidder3Price);

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<AuctionContext> contextArgumentCaptor = ArgumentCaptor.forClass(AuctionContext.class);
        verify(bidResponseCreator).create(contextArgumentCaptor.capture(), any(), any());
        verify(currencyService).convertCurrency(eq(bidder1Price), eq(bidRequest), eq("EUR"), eq("USD"));
        verify(currencyService).convertCurrency(eq(bidder2Price), eq(bidRequest), eq("GBP"), eq("USD"));
        verify(currencyService).convertCurrency(eq(bidder3Price), eq(bidRequest), eq("USD"), eq("USD"));
        verifyNoMoreInteractions(currencyService);

        assertThat(contextArgumentCaptor.getValue().getAuctionParticipations())
                .hasSize(3)
                .extracting(AuctionParticipation::getBidderResponse)
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
                        Bid.builder().id("bidId").impid("impId").price(price)
                                .ext(mapper.valueToTree(singletonMap("bidExt", 1))).build(), banner, null))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder.app(App.builder()
                        .publisher(Publisher.builder().id("1001").build()).build()));

        // when
        final AuctionContext result = target.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(result.getBidResponse().getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(bid -> toExtBidPrebid(bid.getExt()).getEvents())
                .containsNull();
    }

    @Test
    public void shouldIncrementCommonMetrics() {
        // given
        given(httpBidderRequester.requestBids(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(singletonList(
                        givenBidderBid(Bid.builder().impid("impId").price(TEN).build())))));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someAlias", 1)),
                builder -> builder
                        .site(Site.builder().publisher(Publisher.builder().id("accountId").build()).build())
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .aliases(singletonMap("someAlias", "someBidder"))
                                .build())));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        verify(metrics).updateRequestBidderCardinalityMetric(1);
        verify(metrics).updateAccountRequestMetrics(any(), eq(MetricName.openrtb2web));
        verify(metrics).updateAdapterRequestTypeAndNoCookieMetrics(
                eq("someBidder"), eq(MetricName.openrtb2web), eq(true));
        verify(metrics).updateAdapterResponseTime(eq("someBidder"), any(), anyInt());
        verify(metrics).updateAdapterRequestGotbidsMetrics(eq("someBidder"), any());
        verify(metrics).updateAdapterBidMetrics(
                eq("someBidder"), any(), eq(10000L), eq(false), eq("banner"));
    }

    @Test
    public void shouldCallUpdateCookieMetricsWithExpectedValue() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.app(App.builder().build()));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        verify(metrics).updateAdapterRequestTypeAndNoCookieMetrics(
                eq("someBidder"), eq(MetricName.openrtb2web), eq(false));
    }

    @Test
    public void shouldUseEmptyStringIfPublisherIdIsEmpty() {
        // given
        given(httpBidderRequester.requestBids(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(singletonList(
                        givenBidderBid(Bid.builder().price(TEN).build())))));
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));
        final Account account = Account.builder().id("").build();

        // when
        target.holdAuction(givenRequestContext(bidRequest, account));

        // then
        verify(metrics).updateAccountRequestMetrics(eq(account), eq(MetricName.openrtb2web));
    }

    @Test
    public void shouldIncrementNoBidRequestsMetric() {
        // given
        given(httpBidderRequester.requestBids(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(emptyList())));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        verify(metrics).updateAdapterRequestNobidMetrics(eq("someBidder"), any());
    }

    @Test
    public void shouldIncrementGotBidsAndErrorMetricsIfBidderReturnsBidAndDifferentErrors() {
        // given
        given(httpBidderRequester.requestBids(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(BidderSeatBid.builder()
                        .bids(singletonList(givenBidderBid(Bid.builder().impid("impId").price(TEN).build())))
                        .errors(asList(
                                // two identical errors to verify corresponding metric is submitted only once
                                BidderError.badInput("rubicon error"),
                                BidderError.badInput("rubicon error"),
                                BidderError.badServerResponse("rubicon error"),
                                BidderError.failedToRequestBids("rubicon failed to request bids"),
                                BidderError.timeout("timeout error"),
                                BidderError.generic("timeout error")))
                        .build()));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        verify(metrics).updateAdapterRequestGotbidsMetrics(eq("someBidder"), any());
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
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        verify(bidResponsePostProcessor).postProcess(
                any(),
                same(uidsCookie),
                same(bidRequest),
                any(),
                eq(Account.builder()
                        .id("accountId")
                        .auction(AccountAuctionConfig.builder()
                                .events(AccountEventsConfig.of(true))
                                .build())
                        .build()));
    }

    @Test
    public void shouldReturnBidsWithAdjustedPricesWhenAdjustmentFactorPresent() {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenSeatBid(singletonList(
                givenBidderBid(Bid.builder().impid("impId").price(BigDecimal.valueOf(2)).build()))));

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

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> capturedParticipations = captureAuctionParticipations();
        assertThat(capturedParticipations)
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
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenSeatBid(singletonList(
                BidderBid.of(Bid.builder().impid("123").price(BigDecimal.valueOf(2)).build(), video, null))));

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

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> capturedParticipations = captureAuctionParticipations();
        assertThat(capturedParticipations)
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
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenSeatBid(singletonList(
                BidderBid.of(Bid.builder().impid("123").price(BigDecimal.valueOf(2)).build(), video, null))));

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

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> capturedParticipations = captureAuctionParticipations();
        assertThat(capturedParticipations)
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
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenSeatBid(List.of(
                BidderBid.of(Bid.builder().impid("1234").price(BigDecimal.valueOf(2)).build(), video, null))));

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

        // when
        target.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        final List<AuctionParticipation> capturedParticipations = captureAuctionParticipations();
        assertThat(capturedParticipations)
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
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenSeatBid(List.of(
                BidderBid.of(Bid.builder().impid("123").price(BigDecimal.valueOf(2)).build(), video, null))));

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

        // when
        target.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        final List<AuctionParticipation> capturedParticipations = captureAuctionParticipations();
        assertThat(capturedParticipations)
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
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenSeatBid(List.of(
                givenBidderBid(Bid.builder().price(BigDecimal.valueOf(2)).build()),
                BidderBid.builder().type(xNative).bid(givenBid(identity())).build(),
                BidderBid.builder().type(audio).bid(givenBid(identity())).build())));

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

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> capturedParticipations = captureAuctionParticipations();
        assertThat(capturedParticipations)
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
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("bidder", bidder, givenSeatBid(singletonList(
                givenBidderBid(Bid.builder().price(BigDecimal.valueOf(2)).build()))));

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

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> capturedParticipations = captureAuctionParticipations();
        assertThat(capturedParticipations)
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
        final Bidder<?> bidder = mock(Bidder.class);

        givenBidder("bidder", bidder, givenSeatBid(singletonList(
                givenBidderBid(Bid.builder().impid("impId").price(BigDecimal.ONE).build()))));

        final ExtRequestBidAdjustmentFactors givenAdjustments = ExtRequestBidAdjustmentFactors.builder().build();
        givenAdjustments.addFactor("some-other-bidder", BigDecimal.TEN);

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(emptyMap())
                        .auctiontimestamp(1000L)
                        .currency(ExtRequestCurrency.of(null, false))
                        .bidadjustmentfactors(givenAdjustments)
                        .build())));

        // when
        final AuctionContext result = target.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(result.getBidResponse().getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(BigDecimal.ONE);
    }

    @Test
    public void shouldReturnBidsAcceptedByPriceFloorEnforcer() {
        // given
        final BidderBid bidToAccept =
                givenBidderBid(Bid.builder().id("bidId1").impid("impId1").price(ONE).build(), "USD");
        final BidderBid bidToReject =
                givenBidderBid(Bid.builder().id("bidId2").impid("impId2").price(TEN).build(), "USD");

        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(List.of(bidToAccept, bidToReject)));

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

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> capturedParticipations = captureAuctionParticipations();
        assertThat(capturedParticipations)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .containsExactly(bidToAccept);
    }

    @Test
    public void shouldReturnBidsAcceptedByDsaEnforcer() {
        // given
        final BidderBid bidToAccept =
                givenBidderBid(Bid.builder().id("bidId1").impid("impId1").price(ONE).build(), "USD");
        final BidderBid bidToReject =
                givenBidderBid(Bid.builder().id("bidId2").impid("impId2").price(TEN).build(), "USD");

        givenBidder("bidder1", mock(Bidder.class), givenSeatBid(List.of(bidToAccept, bidToReject)));

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

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> capturedParticipations = captureAuctionParticipations();
        assertThat(capturedParticipations)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .containsExactly(bidToAccept);
    }

    @Test
    public void shouldReturnBidResponseModifiedByAuctionResponseHooks() {
        // given
        given(httpBidderRequester.requestBids(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(emptyList())));

        doAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(
                false,
                AuctionResponsePayloadImpl.of(BidResponse.builder().id("bidResponseId").build()))))
                .when(hookStageExecutor).executeAuctionResponseStage(any(), any());

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder", 2)));

        // when
        final AuctionContext auctionContext = target.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(auctionContext.getBidResponse())
                .isEqualTo(BidResponse.builder().id("bidResponseId").build());
    }

    @Test
    public void shouldReturnEmptyBidResponseWhenRequestIsRejected() {
        // given
        final AuctionContext auctionContext = AuctionContext.builder()
                .hookExecutionContext(HookExecutionContext.of(Endpoint.openrtb2_auction))
                .debugContext(DebugContext.empty())
                .requestRejected(true)
                .build();

        // when
        final AuctionContext result = target.holdAuction(auctionContext).result();

        // then
        final BidResponse emptyResponse = BidResponse.builder()
                .seatbid(emptyList())
                .build();

        verifyNoInteractions(storedResponseProcessor, httpBidderRequester, bidResponseCreator);
        verify(hookStageExecutor).executeAuctionResponseStage(eq(emptyResponse), any());
        assertThat(result.getBidResponse()).isEqualTo(emptyResponse);
    }

    @Test
    public void shouldReturnBidResponseWithHooksDebugInfoWhenAuctionHappened() {
        // given
        given(httpBidderRequester.requestBids(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(emptyList())));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder", 2)));
        final AuctionContext auctionContext = givenRequestContext(bidRequest).toBuilder()
                .hookExecutionContext(HookExecutionContext.of(
                        Endpoint.openrtb2_auction,
                        stageOutcomes(givenAppliedToImpl(identity()))))
                .debugContext(DebugContext.of(true, true, null))
                .build();

        // when
        final AuctionContext result = target.holdAuction(auctionContext).result();

        // then
        final BidResponse bidResponse = result.getBidResponse();
        assertThat(bidResponse.getExt()).isNotNull();
        assertThat(bidResponse.getExt().getPrebid()).isNotNull();
        final ExtModules extModules = bidResponse.getExt().getPrebid().getModules();
        assertThat(extModules).isNotNull();

        assertThat(extModules.getErrors())
                .hasSize(3)
                .hasEntrySatisfying("module1", moduleErrors -> assertThat(moduleErrors)
                        .hasSize(2)
                        .hasEntrySatisfying("hook1", hookErrors -> assertThat(hookErrors)
                                .containsOnly("error message 1-1 1", "error message 1-1 2"))
                        .hasEntrySatisfying("hook2", hookErrors -> assertThat(hookErrors)
                                .containsOnly(
                                        "error message 1-2 1",
                                        "error message 1-2 2",
                                        "error message 1-2 3",
                                        "error message 1-2 4")))
                .hasEntrySatisfying("module2", moduleErrors -> assertThat(moduleErrors)
                        .hasSize(1)
                        .hasEntrySatisfying("hook1", hookErrors -> assertThat(hookErrors)
                                .containsOnly("error message 2-1 1", "error message 2-1 2")))
                .hasEntrySatisfying("module3", moduleErrors -> assertThat(moduleErrors)
                        .hasSize(1)
                        .hasEntrySatisfying("hook1", hookErrors -> assertThat(hookErrors)
                                .containsOnly("error message 3-1 1", "error message 3-1 2")));
        assertThat(extModules.getWarnings())
                .hasSize(3)
                .hasEntrySatisfying("module1", moduleErrors -> assertThat(moduleErrors)
                        .hasSize(2)
                        .hasEntrySatisfying("hook1", hookErrors -> assertThat(hookErrors)
                                .containsOnly("warning message 1-1 1", "warning message 1-1 2"))
                        .hasEntrySatisfying("hook2", hookErrors -> assertThat(hookErrors)
                                .containsOnly(
                                        "warning message 1-2 1",
                                        "warning message 1-2 2",
                                        "warning message 1-2 3",
                                        "warning message 1-2 4")))
                .hasEntrySatisfying("module2", moduleErrors -> assertThat(moduleErrors)
                        .hasSize(1)
                        .hasEntrySatisfying("hook1", hookErrors -> assertThat(hookErrors)
                                .containsOnly("warning message 2-1 1", "warning message 2-1 2")))
                .hasEntrySatisfying("module3", moduleErrors -> assertThat(moduleErrors)
                        .hasSize(1)
                        .hasEntrySatisfying("hook1", hookErrors -> assertThat(hookErrors)
                                .containsOnly("warning message 3-1 1", "warning message 3-1 2")));

        assertThat(extModules.getTrace()).isNull();
    }

    @Test
    public void shouldReturnBidResponseWithHooksBasicTraceInfoWhenAuctionHappened() {
        // given
        given(httpBidderRequester.requestBids(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(emptyList())));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder", 2)));
        final AuctionContext auctionContext = givenRequestContext(bidRequest).toBuilder()
                .hookExecutionContext(HookExecutionContext.of(
                        Endpoint.openrtb2_auction,
                        stageOutcomes(givenAppliedToImpl(identity()))))
                .debugContext(DebugContext.of(false, false, TraceLevel.basic))
                .build();

        // when
        final AuctionContext result = target.holdAuction(auctionContext).result();

        // then
        final BidResponse bidResponse = result.getBidResponse();
        assertThat(bidResponse.getExt()).isNotNull();
        assertThat(bidResponse.getExt().getPrebid()).isNotNull();
        final ExtModules extModules = bidResponse.getExt().getPrebid().getModules();
        assertThat(extModules).isNotNull();

        assertThat(extModules.getErrors()).isNull();
        assertThat(extModules.getWarnings()).isNull();

        assertThat(extModules.getTrace()).isEqualTo(ExtModulesTrace.of(
                16L,
                asList(
                        ExtModulesTraceStage.of(
                                Stage.entrypoint,
                                12L,
                                singletonList(ExtModulesTraceStageOutcome.of(
                                        "http-request",
                                        12L,
                                        asList(
                                                ExtModulesTraceGroup.of(
                                                        6L,
                                                        asList(
                                                                ExtModulesTraceInvocationResult.builder()
                                                                        .hookId(HookId.of("module1", "hook1"))
                                                                        .executionTime(4L)
                                                                        .status(ExecutionStatus.success)
                                                                        .message("Message 1-1")
                                                                        .action(ExecutionAction.update)
                                                                        .build(),
                                                                ExtModulesTraceInvocationResult.builder()
                                                                        .hookId(HookId.of("module1", "hook2"))
                                                                        .executionTime(6L)
                                                                        .status(ExecutionStatus.invocation_failure)
                                                                        .message("Message 1-2")
                                                                        .build())),
                                                ExtModulesTraceGroup.of(
                                                        6L,
                                                        asList(
                                                                ExtModulesTraceInvocationResult.builder()
                                                                        .hookId(HookId.of("module1", "hook2"))
                                                                        .executionTime(4L)
                                                                        .status(ExecutionStatus.success)
                                                                        .message("Message 1-2")
                                                                        .action(ExecutionAction.no_action)
                                                                        .build(),
                                                                ExtModulesTraceInvocationResult.builder()
                                                                        .hookId(HookId.of("module2", "hook1"))
                                                                        .executionTime(6L)
                                                                        .status(ExecutionStatus.timeout)
                                                                        .message("Message 2-1")
                                                                        .build())))))),
                        ExtModulesTraceStage.of(
                                Stage.auction_response,
                                4L,
                                singletonList(ExtModulesTraceStageOutcome.of(
                                        "auction-response",
                                        4L,
                                        singletonList(
                                                ExtModulesTraceGroup.of(
                                                        4L,
                                                        asList(
                                                                ExtModulesTraceInvocationResult.builder()
                                                                        .hookId(HookId.of("module3", "hook1"))
                                                                        .executionTime(4L)
                                                                        .status(ExecutionStatus.success)
                                                                        .message("Message 3-1")
                                                                        .action(ExecutionAction.update)
                                                                        .build(),
                                                                ExtModulesTraceInvocationResult.builder()
                                                                        .hookId(HookId.of("module3", "hook2"))
                                                                        .executionTime(4L)
                                                                        .status(ExecutionStatus.success)
                                                                        .action(ExecutionAction.no_action)
                                                                        .build())))))))));
    }

    @Test
    public void shouldReturnBidResponseWithHooksVerboseTraceInfoWhenAuctionHappened() {
        // given
        given(httpBidderRequester.requestBids(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(emptyList())));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder", 2)));
        final AuctionContext auctionContext = givenRequestContext(bidRequest).toBuilder()
                .hookExecutionContext(HookExecutionContext.of(
                        Endpoint.openrtb2_auction,
                        stageOutcomes(givenAppliedToImpl(identity()))))
                .debugContext(DebugContext.of(false, false, TraceLevel.verbose))
                .build();

        // when
        final AuctionContext result = target.holdAuction(auctionContext).result();

        // then
        final BidResponse bidResponse = result.getBidResponse();
        assertThat(bidResponse.getExt().getPrebid().getModules().getTrace().getStages())
                .anySatisfy(stage -> assertThat(stage.getOutcomes())
                        .anySatisfy(outcome -> assertThat(outcome.getGroups())
                                .anySatisfy(group -> assertThat(group.getInvocationResults())
                                        .anySatisfy(hook -> {
                                            assertThat(hook.getDebugMessages())
                                                    .containsOnly("debug message 1-1 1", "debug message 1-1 2");
                                            assertThat(hook.getAnalyticsTags()).isEqualTo(
                                                    ExtModulesTraceAnalyticsTags.of(singletonList(
                                                            ExtModulesTraceAnalyticsActivity.of(
                                                                    "some-activity",
                                                                    "success",
                                                                    singletonList(ExtModulesTraceAnalyticsResult.of(
                                                                            "success",
                                                                            mapper.createObjectNode(),
                                                                            ExtModulesTraceAnalyticsAppliedTo.builder()
                                                                                    .impIds(asList("impId1", "impId2"))
                                                                                    .request(true)
                                                                                    .build()))))));
                                        }))));
    }

    @Test
    public void shouldReturnProperBidResponseWithAppliedToIfResultImplAppliedToIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder", 2)));
        final AuctionContext auctionContext = givenRequestContext(bidRequest).toBuilder()
                .hookExecutionContext(HookExecutionContext.of(
                        Endpoint.openrtb2_auction,
                        stageOutcomes(givenAppliedToImpl(appliedToImplBuilder -> appliedToImplBuilder
                                .impIds(asList("impId1", "impId2"))
                                .response(true)
                                .request(false)))))
                .debugContext(DebugContext.of(false, false, TraceLevel.verbose))
                .build();

        given(httpBidderRequester.requestBids(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(emptyList())));

        // when
        final AuctionContext result = target.holdAuction(auctionContext).result();

        // then
        final ExtModulesTraceAnalyticsTags givenExtModulesTraceAnalyticsTags =
                ExtModulesTraceAnalyticsTags.of(singletonList(
                        ExtModulesTraceAnalyticsActivity.of(
                                "some-activity",
                                "success",
                                singletonList(
                                        ExtModulesTraceAnalyticsResult.of(
                                                "success",
                                                mapper.createObjectNode(),
                                                ExtModulesTraceAnalyticsAppliedTo.builder()
                                                        .impIds(asList("impId1", "impId2"))
                                                        .response(true)
                                                        .request(null)
                                                        .build())))));

        assertThat(result.getBidResponse().getExt().getPrebid().getModules().getTrace().getStages())
                .flatExtracting(ExtModulesTraceStage::getOutcomes)
                .flatExtracting(ExtModulesTraceStageOutcome::getGroups)
                .flatExtracting(ExtModulesTraceGroup::getInvocationResults)
                .extracting(ExtModulesTraceInvocationResult::getAnalyticsTags)
                .contains(givenExtModulesTraceAnalyticsTags);
    }

    @Test
    public void shouldReturnBidResponseAppliedToRequestNullIfResultImplAppliedToIsNull() {
        // given
        given(httpBidderRequester.requestBids(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(emptyList())));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder", 2)));
        final AuctionContext auctionContext = givenRequestContext(bidRequest).toBuilder()
                .hookExecutionContext(HookExecutionContext.of(
                        Endpoint.openrtb2_auction,
                        stageOutcomes(null)))
                .debugContext(DebugContext.of(false, false, TraceLevel.verbose))
                .build();

        // when
        final AuctionContext result = target.holdAuction(auctionContext).result();

        // then
        final BidResponse bidResponse = result.getBidResponse();
        assertThat(bidResponse.getExt().getPrebid().getModules().getTrace().getStages())
                .anySatisfy(stage -> assertThat(stage.getOutcomes())
                        .anySatisfy(outcome -> assertThat(outcome.getGroups())
                                .anySatisfy(group -> assertThat(group.getInvocationResults())
                                        .anySatisfy(hook -> {
                                            assertThat(hook.getDebugMessages())
                                                    .containsOnly("debug message 1-1 1", "debug message 1-1 2");
                                            assertThat(hook.getAnalyticsTags()).isEqualTo(
                                                    ExtModulesTraceAnalyticsTags.of(singletonList(
                                                            ExtModulesTraceAnalyticsActivity.of(
                                                                    "some-activity",
                                                                    "success",
                                                                    singletonList(ExtModulesTraceAnalyticsResult.of(
                                                                            "success",
                                                                            mapper.createObjectNode(),
                                                                            null))))));
                                        }))));
    }

    @Test
    public void shouldReturnBidResponseWithHooksDebugAndTraceInfoWhenAuctionHappened() {
        // given
        given(httpBidderRequester.requestBids(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(emptyList())));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder", 2)));
        final AuctionContext auctionContext = givenRequestContext(bidRequest).toBuilder()
                .hookExecutionContext(HookExecutionContext.of(
                        Endpoint.openrtb2_auction,
                        stageOutcomes(givenAppliedToImpl(identity()))))
                .debugContext(DebugContext.of(true, true, TraceLevel.basic))
                .build();

        // when
        final AuctionContext result = target.holdAuction(auctionContext).result();

        // then
        final BidResponse bidResponse = result.getBidResponse();
        final ExtModules extModules = bidResponse.getExt().getPrebid().getModules();

        assertThat(extModules.getErrors()).isNotEmpty();
        assertThat(extModules.getWarnings()).isNotEmpty();
        assertThat(extModules.getTrace()).isNotNull();
    }

    @Test
    public void shouldReturnBidResponseWithHooksDebugAndTraceInfoWhenRequestIsRejected() {
        // given
        final AuctionContext auctionContext = AuctionContext.builder()
                .hookExecutionContext(HookExecutionContext.of(
                        Endpoint.openrtb2_auction,
                        stageOutcomes(givenAppliedToImpl(identity()))))
                .debugContext(DebugContext.of(true, true, TraceLevel.basic))
                .requestRejected(true)
                .build();

        // when
        final AuctionContext result = target.holdAuction(auctionContext).result();

        // then
        final BidResponse bidResponse = result.getBidResponse();
        final ExtModules extModules = bidResponse.getExt().getPrebid().getModules();

        assertThat(extModules.getErrors()).isNotEmpty();
        assertThat(extModules.getWarnings()).isNotEmpty();
        assertThat(extModules.getTrace()).isNotNull();
    }

    @Test
    public void shouldReturnBidResponseWithoutHooksTraceInfoWhenNoHooksExecuted() {
        // given
        given(httpBidderRequester.requestBids(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(emptyList())));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder", 2)));
        final AuctionContext auctionContext = givenRequestContext(bidRequest).toBuilder()
                .hookExecutionContext(HookExecutionContext.of(
                        Endpoint.openrtb2_auction,
                        new EnumMap<>(singletonMap(
                                Stage.entrypoint,
                                singletonList(StageExecutionOutcome.of("http-request", emptyList()))))))
                .debugContext(DebugContext.of(false, false, TraceLevel.basic))
                .build();

        // when
        final AuctionContext result = target.holdAuction(auctionContext).result();

        // then
        final BidResponse bidResponse = result.getBidResponse();
        assertThat(bidResponse.getExt()).isNull();
    }

    @Test
    public void shouldIncrementHooksGlobalMetrics() {
        // given
        final AuctionContext auctionContext = AuctionContext.builder()
                .hookExecutionContext(HookExecutionContext.of(
                        Endpoint.openrtb2_auction,
                        stageOutcomes(givenAppliedToImpl(identity()))))
                .debugContext(DebugContext.empty())
                .requestRejected(true)
                .build();

        // when
        target.holdAuction(auctionContext);

        // then
        verify(metrics, times(6)).updateHooksMetrics(anyString(), any(), any(), any(), any(), any());
        verify(metrics).updateHooksMetrics(
                eq("module1"),
                eq(Stage.entrypoint),
                eq("hook1"),
                eq(ExecutionStatus.success),
                eq(4L),
                eq(ExecutionAction.update));
        verify(metrics).updateHooksMetrics(
                eq("module1"),
                eq(Stage.entrypoint),
                eq("hook2"),
                eq(ExecutionStatus.invocation_failure),
                eq(6L),
                isNull());
        verify(metrics).updateHooksMetrics(
                eq("module1"),
                eq(Stage.entrypoint),
                eq("hook2"),
                eq(ExecutionStatus.success),
                eq(4L),
                eq(ExecutionAction.no_action));
        verify(metrics).updateHooksMetrics(
                eq("module2"),
                eq(Stage.entrypoint),
                eq("hook1"),
                eq(ExecutionStatus.timeout),
                eq(6L),
                isNull());
        verify(metrics).updateHooksMetrics(
                eq("module3"),
                eq(Stage.auction_response),
                eq("hook1"),
                eq(ExecutionStatus.success),
                eq(4L),
                eq(ExecutionAction.update));
        verify(metrics).updateHooksMetrics(
                eq("module3"),
                eq(Stage.auction_response),
                eq("hook2"),
                eq(ExecutionStatus.success),
                eq(4L),
                eq(ExecutionAction.no_action));
        verify(metrics, never()).updateAccountHooksMetrics(any(), any(), any(), any());
        verify(metrics, never()).updateAccountModuleDurationMetric(any(), any(), any());
    }

    @Test
    public void shouldIncrementHooksGlobalAndAccountMetrics() {
        // given
        given(httpBidderRequester.requestBids(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenSeatBid(emptyList())));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("bidder", 2)));
        final AuctionContext auctionContext = givenRequestContext(bidRequest).toBuilder()
                .hookExecutionContext(HookExecutionContext.of(
                        Endpoint.openrtb2_auction,
                        stageOutcomes(givenAppliedToImpl(identity()))))
                .debugContext(DebugContext.empty())
                .build();

        // when
        target.holdAuction(auctionContext);

        // then
        verify(metrics, times(6)).updateHooksMetrics(anyString(), any(), any(), any(), any(), any());
        verify(metrics, times(6)).updateAccountHooksMetrics(any(), any(), any(), any());
        verify(metrics).updateAccountHooksMetrics(
                any(),
                eq("module1"),
                eq(ExecutionStatus.success),
                eq(ExecutionAction.update));
        verify(metrics).updateAccountHooksMetrics(
                any(),
                eq("module1"),
                eq(ExecutionStatus.invocation_failure),
                isNull());
        verify(metrics).updateAccountHooksMetrics(
                any(),
                eq("module1"),
                eq(ExecutionStatus.success),
                eq(ExecutionAction.no_action));
        verify(metrics).updateAccountHooksMetrics(
                any(),
                eq("module2"),
                eq(ExecutionStatus.timeout),
                isNull());
        verify(metrics).updateAccountHooksMetrics(
                any(),
                eq("module3"),
                eq(ExecutionStatus.success),
                eq(ExecutionAction.update));
        verify(metrics).updateAccountHooksMetrics(
                any(),
                eq("module3"),
                eq(ExecutionStatus.success),
                eq(ExecutionAction.no_action));
        verify(metrics, times(3)).updateAccountModuleDurationMetric(any(), any(), any());
        verify(metrics).updateAccountModuleDurationMetric(any(), eq("module1"), eq(14L));
        verify(metrics).updateAccountModuleDurationMetric(any(), eq("module2"), eq(6L));
        verify(metrics).updateAccountModuleDurationMetric(any(), eq("module3"), eq(8L));
    }

    @Test
    public void shouldProperPopulateImpExtPrebidEvenIfInExtImpPrebidContainNotCorrectField() {
        // given
        final Imp imp = Imp.builder()
                .ext(mapper.valueToTree(Map.of("prebid",
                        doubleMap("test-field", "test-value", "bidder",
                                Map.of("bidderName", doubleMap("test-host", "unknownHost", "publisher_id", "ps4"))))))
                .build();
        final BidRequest bidRequest = givenBidRequest(singletonList(imp), identity());
        final AuctionContext auctionContext = givenRequestContext(bidRequest);

        given(privacyEnforcementService.mask(any(), anyMap(), any()))
                .willReturn(Future.succeededFuture(singletonList(BidderPrivacyResult.builder()
                        .requestBidder("bidderName")
                        .build())));

        // when
        target.holdAuction(auctionContext);

        // then
        final BidRequest capturedBidRequest = captureBidRequest();
        assertThat(capturedBidRequest.getImp())
                .extracting(Imp::getExt)
                .containsExactly(mapper.valueToTree(doubleMap("prebid", emptyMap(),
                        "bidder", doubleMap("test-host", "unknownHost", "publisher_id", "ps4"))));
    }

    @Test
    public void shouldThrowErrorIfCannotBeParsedImpExtPrebid() {
        // given
        final String prebid = "prebid";
        final String bidderKey = "bidder";
        final String optionKey = "options";
        final String bidderName = "bidderName";
        final String optionValue = "1";
        final Map<String, Map<String, String>> bidderValue =
                singletonMap(bidderName, doubleMap("test-host", "unknownHost", "publisher_id", "ps4"));

        final ObjectNode prebidJsonNodes = mapper.valueToTree(
                singletonMap(prebid, doubleMap(optionKey, optionValue, bidderKey, bidderValue)));

        final Imp imp = Imp.builder().ext(prebidJsonNodes).build();
        final BidRequest bidRequest = givenBidRequest(singletonList(imp), identity());
        final AuctionContext auctionContext = givenRequestContext(bidRequest);

        given(privacyEnforcementService.mask(any(), anyMap(), any()))
                .willReturn(Future.succeededFuture(singletonList(BidderPrivacyResult.builder()
                        .requestBidder(bidderName)
                        .build())));

        // when
        final Future<AuctionContext> result = target.holdAuction(auctionContext);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessageStartingWith("Error decoding imp.ext.prebid: "
                        + "Cannot construct instance of `org.prebid.server.proto.openrtb.ext.request.ExtOptions`");
    }

    @Test
    public void shouldReturnsSourceWithCorrespondingRequestExtPrebidSchainsIfSchainIsNotNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenSingleImp("test"),
                bidRequestBuilder -> bidRequestBuilder.source(Source.builder().build()));
        final AuctionContext auctionContext = givenRequestContext(bidRequest);

        final SupplyChain givenSourceSchain = SupplyChain.of(1, singletonList(null), "", null);
        given(supplyChainResolver.resolveForBidder(any(), any()))
                .willReturn(givenSourceSchain);

        given(privacyEnforcementService.mask(any(), anyMap(), any()))
                .willReturn(Future.succeededFuture(singletonList(BidderPrivacyResult.builder()
                        .requestBidder("bidderName")
                        .build())));

        // when
        target.holdAuction(auctionContext);

        // then
        final BidRequest capturedBidRequest = captureBidRequest();
        assertThat(capturedBidRequest)
                .extracting(BidRequest::getSource)
                .extracting(Source::getSchain)
                .isEqualTo(givenSourceSchain);
    }

    @Test
    public void shouldReduceBidsHavingDealIdWithSameImpIdByBidderWithToleratingNotObtainedBidWithTopDeal() {
        // given
        final Imp imp = givenImp(
                singletonMap("bidder1", 1),
                builder -> builder
                        .id("impId1")
                        .pmp(Pmp.builder()
                                .deals(asList(
                                        Deal.builder().id("dealId1").build(), // top deal, but no response bid
                                        Deal.builder().id("dealId2").build()))
                                .build()));
        final BidRequest bidRequest = givenBidRequest(singletonList(imp), identity());
        final AuctionContext auctionContext = givenRequestContext(bidRequest).toBuilder().build();

        final BidderBid bidderBid = givenBidderBid(
                Bid.builder().id("bidId2").impid("impId1").dealid("dealId2").price(BigDecimal.ONE).build());

        givenBidder(givenSingleSeatBid(bidderBid));

        given(responseBidValidator.validate(any(), any(), any(), any()))
                .willReturn(ValidationResult.success());

        // when
        target.holdAuction(auctionContext);

        // then
        final ArgumentCaptor<AuctionContext> contextArgumentCaptor = ArgumentCaptor.forClass(AuctionContext.class);
        verify(bidResponseCreator).create(contextArgumentCaptor.capture(), any(), any());
        assertThat(contextArgumentCaptor.getValue().getAuctionParticipations()).hasSize(1)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids).hasSize(1)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId)
                .containsOnly("bidId2");
    }

    @Test
    public void shouldReduceBidsHavingDealIdWithSameImpIdByBidderWithToleratingNotObtainedBids() {
        // given
        final Imp imp = givenImp(
                singletonMap("bidder1", 1),
                builder -> builder
                        .id("impId1")
                        .pmp(Pmp.builder()
                                .deals(asList(
                                        Deal.builder().id("dealId1").build(),
                                        Deal.builder().id("dealId2").build()))
                                .build()));
        final BidRequest bidRequest = givenBidRequest(singletonList(imp), identity());
        final AuctionContext auctionContext = givenRequestContext(bidRequest).toBuilder().build();

        givenBidder(givenSeatBid(emptyList()));

        // when
        target.holdAuction(auctionContext);

        // then
        final ArgumentCaptor<AuctionContext> contextArgumentCaptor = ArgumentCaptor.forClass(AuctionContext.class);
        verify(bidResponseCreator).create(contextArgumentCaptor.capture(), any(), any());

        assertThat(contextArgumentCaptor.getValue().getAuctionParticipations()).hasSize(1)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids).isEmpty();
    }

    @Test
    public void shouldResponseWithEmptySeatBidIfBidderNotSupportProvidedMediaTypes() {
        // given
        final Imp imp = givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1"));
        final BidRequest bidRequest = givenBidRequest(singletonList(imp), identity());
        final AuctionContext auctionContext = givenRequestContext(bidRequest).toBuilder().build();

        given(mediaTypeProcessor.process(any(), anyString(), any(), any()))
                .willReturn(MediaTypeProcessingResult.rejected(Collections.singletonList(
                        BidderError.badInput("MediaTypeProcessor error."))));
        given(bidResponseCreator.create(
                argThat(argument -> argument.getAuctionParticipations().get(0)
                        .getBidderResponse()
                        .equals(BidderResponse.of(
                                "bidder1",
                                BidderSeatBid.builder()
                                        .warnings(Collections.singletonList(
                                                BidderError.badInput("MediaTypeProcessor error.")))
                                        .build(),
                                0))),
                any(),
                any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().id("uniqId").build()));

        // when
        final Future<AuctionContext> result = target.holdAuction(auctionContext);

        // then
        assertThat(result.result())
                .extracting(AuctionContext::getBidResponse)
                .isEqualTo(BidResponse.builder().id("uniqId").build());
    }

    @Test
    public void shouldConvertBidRequestOpenRTBVersionToConfiguredByBidder() {
        // given
        given(ortbVersionConversionManager.convertFromAuctionSupportedVersion(any(), any())).willAnswer(
                invocation -> ((BidRequest) invocation.getArgument(0))
                        .toBuilder()
                        .source(null)
                        .build());

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap("bidderName", 1)),
                request -> request.source(Source.builder().tid("uniqTid").build()));
        final AuctionContext auctionContext = givenRequestContext(bidRequest);

        // when
        target.holdAuction(auctionContext);

        // then
        final ArgumentCaptor<BidderRequest> argumentCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(any(), argumentCaptor.capture(), any(), any(), any(), any(), anyBoolean());

        assertThat(argumentCaptor.getValue())
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getSource)
                .isNull();
    }

    @Test
    public void shouldPassAdjustedTimeoutToAdapterAndToBidResponseCreator() {
        // given
        given(timeoutResolver.adjustForBidder(anyLong(), eq(90), anyLong()))
                .willReturn(400L);
        given(timeoutResolver.adjustForRequest(anyLong(), anyLong()))
                .willReturn(450L);

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap("bidderName", 1)),
                request -> request.source(Source.builder().tid("uniqTid").build()));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidderRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        final ArgumentCaptor<Long> timeoutCaptor = ArgumentCaptor.forClass(Long.class);
        verify(httpBidderRequester).requestBids(
                any(),
                bidderRequestCaptor.capture(),
                any(),
                any(),
                any(),
                any(),
                anyBoolean());
        verify(timeoutFactory).create(anyLong(), timeoutCaptor.capture());
        assertThat(bidderRequestCaptor.getValue().getBidRequest().getTmax()).isEqualTo(400L);
        assertThat(timeoutCaptor.getAllValues()).containsExactly(450L);
    }

    private void givenTarget(boolean enabledStrictAppSiteDoohValidation) {
        target = new ExchangeService(
                0,
                bidderCatalog,
                storedResponseProcessor,
                privacyEnforcementService,
                fpdResolver,
                supplyChainResolver,
                debugResolver,
                mediaTypeProcessor,
                uidUpdater,
                timeoutResolver,
                timeoutFactory,
                ortbVersionConversionManager,
                httpBidderRequester,
                responseBidValidator,
                currencyService,
                bidResponseCreator,
                bidResponsePostProcessor,
                hookStageExecutor,
                httpInteractionLogger,
                priceFloorAdjuster,
                priceFloorEnforcer,
                dsaEnforcer,
                bidAdjustmentFactorResolver,
                metrics,
                clock,
                jacksonMapper,
                criteriaLogManager,
                enabledStrictAppSiteDoohValidation);
    }

    private AuctionContext givenRequestContext(BidRequest bidRequest) {
        return givenRequestContext(
                bidRequest,
                Account.builder()
                        .id("accountId")
                        .auction(AccountAuctionConfig.builder()
                                .events(AccountEventsConfig.of(true))
                                .build())
                        .build());
    }

    private AuctionContext givenRequestContext(BidRequest bidRequest, Account account) {
        return AuctionContext.builder()
                .httpRequest(HttpRequestContext.builder().headers(CaseInsensitiveMultiMap.empty()).build())
                .uidsCookie(uidsCookie)
                .bidRequest(bidRequest)
                .debugWarnings(new ArrayList<>())
                .account(account)
                .requestTypeMetric(MetricName.openrtb2web)
                .timeoutContext(TimeoutContext.of(clock.millis(), timeout, 90))
                .hookExecutionContext(HookExecutionContext.of(Endpoint.openrtb2_auction))
                .debugContext(DebugContext.empty())
                .bidRejectionTrackers(new HashMap<>())
                .activityInfrastructure(activityInfrastructure)
                .build();
    }

    private BidRequest captureBidRequest() {
        final ArgumentCaptor<BidderRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(any(), bidRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());
        return bidRequestCaptor.getValue().getBidRequest();
    }

    private List<AuctionParticipation> captureAuctionParticipations() {
        final ArgumentCaptor<AuctionContext> contextArgumentCaptor = ArgumentCaptor.forClass(AuctionContext.class);
        verify(bidResponseCreator).create(contextArgumentCaptor.capture(), any(), any());
        return contextArgumentCaptor.getValue().getAuctionParticipations();
    }

    private static BidRequest givenBidRequest(List<Imp> imp,
                                              UnaryOperator<BidRequestBuilder> bidRequestBuilderCustomizer) {

        return bidRequestBuilderCustomizer
                .apply(BidRequest.builder().cur(singletonList("USD")).imp(imp).tmax(500L))
                .build();
    }

    private static BidRequest givenBidRequest(List<Imp> imp) {
        return givenBidRequest(imp, identity());
    }

    private static <T> Imp givenImp(T ext, Function<ImpBuilder, ImpBuilder> impBuilderCustomizer) {
        return impBuilderCustomizer.apply(Imp.builder()
                        .id(UUID.randomUUID().toString())
                        .ext(mapper.valueToTree(singletonMap(
                                "prebid", ext != null ? singletonMap("bidder", ext) : emptyMap()))))
                .build();
    }

    private static <T> List<Imp> givenSingleImp(String impId, T ext) {
        return singletonList(givenImp(ext, builder -> builder.id(impId)));
    }

    private static <T> List<Imp> givenSingleImp(T ext) {
        return singletonList(givenImp(ext, identity()));
    }

    private void givenBidder(BidderSeatBid response) {
        given(httpBidderRequester.requestBids(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(response));
    }

    private void givenBidder(String bidderName, Bidder<?> bidder, BidderSeatBid response) {
        doReturn(bidder).when(bidderCatalog).bidderByName(eq(bidderName));
        given(httpBidderRequester.requestBids(same(bidder), any(), any(), any(), any(), any(), anyBoolean()))
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
        return BidderSeatBid.of(bids);
    }

    private static BidderSeatBid givenSingleSeatBid(BidderBid bid) {
        return givenSeatBid(singletonList(bid));
    }

    private static BidderSeatBid givenEmptySeatBid() {
        return givenSeatBid(emptyList());
    }

    private static BidderBid givenBidderBid(Bid bid) {
        return BidderBid.of(bid, BidType.banner, null);
    }

    private static BidderBid givenBidderBid(Bid bid, String cur) {
        return BidderBid.of(bid, BidType.banner, cur);
    }

    private static Bid givenBid(Function<Bid.BidBuilder, Bid.BidBuilder> bidBuilder) {
        return bidBuilder.apply(Bid.builder()
                        .id("bidId")
                        .price(BigDecimal.ONE)
                        .ext(mapper.valueToTree(ExtPrebid.of(ExtBidPrebid.builder().build(), null))))
                .build();
    }

    private static FledgeAuctionConfig givenFledgeAuctionConfig(String impId) {
        return FledgeAuctionConfig.builder()
                .impId(impId)
                .config(mapper.createObjectNode().put("references", impId))
                .build();
    }

    private static <K, V> Map<K, V> doubleMap(K key1, V value1, K key2, V value2) {
        final Map<K, V> map = new HashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }

    private static ExtBidPrebid toExtBidPrebid(ObjectNode ext) {
        try {
            return mapper.treeToValue(ext.get("prebid"), ExtBidPrebid.class);
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
        given(bidResponseCreator.create(any(), any(), any()))
                .willReturn(Future.succeededFuture(givenBidResponseWithBids(bids)));
    }

    private void givenBidResponseCreator(Map<String, List<ExtBidderError>> errors) {
        given(bidResponseCreator.create(any(), any(), any()))
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
                .ext(ExtBidResponse.builder()
                        .errors(errors)
                        .build())
                .build();
    }

    private void testUserEidsPermissionFiltering(List<Eid> givenUserEids,
                                                 List<ExtRequestPrebidDataEidPermissions> givenEidPermissions,
                                                 Map<String, String> givenAlises,
                                                 List<Eid> expectedExtUserEids) {
        // given
        final Bidder<?> bidder = mock(Bidder.class);
        givenBidder("someBidder", bidder, givenEmptySeatBid());
        final Map<String, Integer> bidderToGdpr = singletonMap("someBidder", 1);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(bidderToGdpr),
                builder -> builder
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .aliases(givenAlises)
                                .data(ExtRequestPrebidData.of(null, givenEidPermissions))
                                .build()))
                        .user(User.builder()
                                .eids(givenUserEids)
                                .build()));

        // when
        target.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<BidderRequest> bidderRequestCaptor = ArgumentCaptor.forClass(BidderRequest.class);
        verify(httpBidderRequester)
                .requestBids(any(), bidderRequestCaptor.capture(), any(), any(), any(), any(), anyBoolean());
        final List<BidderRequest> capturedBidRequests = bidderRequestCaptor.getAllValues();
        assertThat(capturedBidRequests)
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getUser)
                .flatExtracting(User::getEids)
                .isEqualTo(expectedExtUserEids);
    }

    private static AppliedToImpl givenAppliedToImpl(
            Function<AppliedToImpl.AppliedToImplBuilder, AppliedToImpl.AppliedToImplBuilder> appliedToImplBuilder) {
        return appliedToImplBuilder.apply(AppliedToImpl.builder()
                        .impIds(asList("impId1", "impId2"))
                        .request(true))
                .build();
    }

    private static EnumMap<Stage, List<StageExecutionOutcome>> stageOutcomes(AppliedToImpl appliedToImp) {
        final Map<Stage, List<StageExecutionOutcome>> stageOutcomes = new HashMap<>();

        stageOutcomes.put(Stage.entrypoint, singletonList(StageExecutionOutcome.of(
                "http-request",
                asList(
                        GroupExecutionOutcome.of(asList(
                                HookExecutionOutcome.builder()
                                        .hookId(HookId.of("module1", "hook1"))
                                        .executionTime(4L)
                                        .status(ExecutionStatus.success)
                                        .message("Message 1-1")
                                        .action(ExecutionAction.update)
                                        .errors(asList("error message 1-1 1", "error message 1-1 2"))
                                        .warnings(asList("warning message 1-1 1", "warning message 1-1 2"))
                                        .debugMessages(asList("debug message 1-1 1", "debug message 1-1 2"))
                                        .analyticsTags(TagsImpl.of(singletonList(
                                                ActivityImpl.of(
                                                        "some-activity",
                                                        "success",
                                                        singletonList(ResultImpl.of(
                                                                "success",
                                                                mapper.createObjectNode(),
                                                                appliedToImp))))))
                                        .build(),
                                HookExecutionOutcome.builder()
                                        .hookId(HookId.of("module1", "hook2"))
                                        .executionTime(6L)
                                        .status(ExecutionStatus.invocation_failure)
                                        .message("Message 1-2")
                                        .errors(asList("error message 1-2 1", "error message 1-2 2"))
                                        .warnings(asList("warning message 1-2 1", "warning message 1-2 2"))
                                        .build())),
                        GroupExecutionOutcome.of(asList(
                                HookExecutionOutcome.builder()
                                        .hookId(HookId.of("module1", "hook2"))
                                        .executionTime(4L)
                                        .status(ExecutionStatus.success)
                                        .message("Message 1-2")
                                        .action(ExecutionAction.no_action)
                                        .errors(asList("error message 1-2 3", "error message 1-2 4"))
                                        .warnings(asList("warning message 1-2 3", "warning message 1-2 4"))
                                        .build(),
                                HookExecutionOutcome.builder()
                                        .hookId(HookId.of("module2", "hook1"))
                                        .executionTime(6L)
                                        .status(ExecutionStatus.timeout)
                                        .message("Message 2-1")
                                        .errors(asList("error message 2-1 1", "error message 2-1 2"))
                                        .warnings(asList("warning message 2-1 1", "warning message 2-1 2"))
                                        .build()))))));

        stageOutcomes.put(Stage.auction_response, singletonList(StageExecutionOutcome.of(
                "auction-response",
                singletonList(
                        GroupExecutionOutcome.of(asList(
                                HookExecutionOutcome.builder()
                                        .hookId(HookId.of("module3", "hook1"))
                                        .executionTime(4L)
                                        .status(ExecutionStatus.success)
                                        .message("Message 3-1")
                                        .action(ExecutionAction.update)
                                        .errors(asList("error message 3-1 1", "error message 3-1 2"))
                                        .warnings(asList("warning message 3-1 1", "warning message 3-1 2"))
                                        .build(),
                                HookExecutionOutcome.builder()
                                        .hookId(HookId.of("module3", "hook2"))
                                        .executionTime(4L)
                                        .status(ExecutionStatus.success)
                                        .action(ExecutionAction.no_action)
                                        .build()))))));

        return new EnumMap<>(stageOutcomes);
    }
}
