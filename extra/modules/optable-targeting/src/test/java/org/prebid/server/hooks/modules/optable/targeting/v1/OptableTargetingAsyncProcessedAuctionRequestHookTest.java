package org.prebid.server.hooks.modules.optable.targeting.v1;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdActivityMask;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.execution.timeout.TimeoutFactory;
import org.prebid.server.hooks.execution.model.ExecutionPlan;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.OptableAttributes;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.BidderEnrichmentSampler;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.CompositeHookExecutionPlan;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.ConfigResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableTargetingFlowResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.TargetingRequestExecutor;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableTargeting;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.settings.model.Account;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OptableTargetingAsyncProcessedAuctionRequestHookTest extends BaseOptableTest {

    private ConfigResolver configResolver;

    @Mock
    private OptableTargeting optableTargeting;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private UserFpdActivityMask userFpdActivityMask;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private IpAddressHelper ipAddressHelper;

    @Mock
    private AuctionRequestPayload auctionRequestPayload;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private AuctionInvocationContext invocationContext;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private ActivityInfrastructure activityInfrastructure;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private Timeout timeout;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private TimeoutFactory timeoutFactory;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private BidderEnrichmentSampler bidderEnrichmentSampler;

    private TargetingRequestExecutor targetingRequestExecutor;

    private OptableTargetingAsyncProcessedAuctionRequestHook target;

    @BeforeEach
    void setUp() {
        when(userFpdActivityMask.maskUser(any(), anyBoolean(), anyBoolean()))
                .thenAnswer(answer -> answer.getArgument(0));
        when(userFpdActivityMask.maskDevice(any(), anyBoolean(), anyBoolean()))
                .thenAnswer(answer -> answer.getArgument(0));
        configResolver = new ConfigResolver(mapper, jsonMerger, givenOptableTargetingProperties(false));
        targetingRequestExecutor = new TargetingRequestExecutor(
                optableTargeting, userFpdActivityMask, timeoutFactory, ipAddressHelper, 0.01);
        target = new OptableTargetingAsyncProcessedAuctionRequestHook(
                configResolver, givenFlowResolver());

        when(invocationContext.accountConfig()).thenReturn(givenAccountConfig(true));
        when(invocationContext.auctionContext()).thenReturn(
                givenAuctionContext(activityInfrastructure, timeout, Account.builder().id("accountId").build()));
        when(invocationContext.timeout()).thenReturn(timeout);
        when(activityInfrastructure.isAllowed(any(), any())).thenReturn(true);
        when(timeout.remaining()).thenReturn(1000L);
    }

    private OptableTargetingFlowResolver givenFlowResolver() {
        return new OptableTargetingFlowResolver(
                bidderEnrichmentSampler,
                targetingRequestExecutor,
                CompositeHookExecutionPlan.of(ExecutionPlan.empty(), null),
                0.01);
    }

    @Test
    void codeShouldReturnRightCode() {
        // when and then
        assertThat(target.code()).isEqualTo("optable-targeting-async-processed-auction-request-hook");
    }

    @Test
    void callShouldReturnResultWithNoActionWhenModuleContextIsAbsent() {
        // given
        when(invocationContext.moduleContext()).thenReturn(null);

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target.call(auctionRequestPayload,
                invocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AuctionRequestPayload> result = future.result();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.no_action, InvocationResult::action)
                .extracting(InvocationResult::errors).isNull();
        assertThat(result.payloadUpdate()).isNull();
    }

    @Test
    void callShouldReturnResultWithNoActionWhenEarlyOptableCallIsInitialized() {
        // given
        final ModuleContext moduleContext = new ModuleContext();
        moduleContext.setEarlyCallInitializationCompleted(true);
        when(invocationContext.moduleContext()).thenReturn(moduleContext);
        when(auctionRequestPayload.bidRequest()).thenReturn(givenBidRequest());
        when(optableTargeting.getTargeting(any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture(givenTargetingResult()));
        moduleContext.setOptableTargetingCall(
                targetingRequestExecutor.makeRequest(
                        auctionRequestPayload,
                        invocationContext,
                        givenOptableTargetingProperties("key", "tenant", "origin", false),
                        null));

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target.call(auctionRequestPayload,
                invocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AuctionRequestPayload> result = future.result();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.no_action, InvocationResult::action)
                .extracting(InvocationResult::errors).isNull();
        assertThat(result.payloadUpdate()).isNull();
        assertThat(moduleContext.getOptableTargetingCall()).isNotNull();
        assertThat(moduleContext.getOptableTargetingCall().succeeded()).isTrue();
    }

    @Test
    void callShouldRetryEarlyNetworkCallInitializationWhenItWasNotCompletedOnRawAuctionRequestHookStep() {
        // given
        final ModuleContext moduleContext = new ModuleContext();
        moduleContext.setEarlyCallInitializationCompleted(false);

        final TargetingResult targetingResult = givenTargetingResult();
        when(invocationContext.moduleContext()).thenReturn(moduleContext);
        when(auctionRequestPayload.bidRequest()).thenReturn(givenBidRequest());
        when(optableTargeting.getTargeting(any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture(targetingResult));
        when(bidderEnrichmentSampler.sample(any(), any())).thenReturn(Set.of("bidder"));

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target.call(auctionRequestPayload,
                invocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AuctionRequestPayload> result = future.result();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.update, InvocationResult::action)
                .extracting(InvocationResult::errors).isNull();
        assertThat(result.analyticsTags()).isNull();
        assertThat(moduleContext.getBiddersToEnrich()).containsExactly("bidder");
        assertThat(moduleContext.getOptableTargetingCall()).isNotNull();
        assertThat(moduleContext.getOptableTargetingCall().succeeded()).isTrue();
        assertThat(moduleContext.getOptableTargetingCall().result()).isSameAs(targetingResult);

        final BidRequest bidRequest = result
                .payloadUpdate()
                .apply(AuctionRequestPayloadImpl.of(givenBidRequest()))
                .bidRequest();
        assertThat(bidRequest.getUser().getEids()).isNull();
        assertThat(bidRequest.getUser().getData()).isNull();
    }

    @Test
    void callShouldResolveGdprAttributesFromTcfContextWhenGdprScopeComesFromDefaultValue() {
        // given
        final ModuleContext moduleContext = new ModuleContext();
        moduleContext.setEarlyCallInitializationCompleted(false);

        final TcfContext tcfContext = TcfContext.builder()
                .inGdprScope(true)
                .consentString("consent")
                .consentValid(true)
                .build();
        final BidRequest bidRequest = givenBidRequest(bidRequestCustomizer ->
                bidRequestCustomizer.user(User.builder().consent("consent").build()));
        when(invocationContext.moduleContext()).thenReturn(moduleContext);
        givenAuctionContextWithTcfContext(tcfContext, bidRequest);
        when(optableTargeting.getTargeting(any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture(givenTargetingResult()));
        when(bidderEnrichmentSampler.sample(any(), any())).thenReturn(Set.of("bidder"));

        // when
        target.call(auctionRequestPayload, invocationContext);

        // then
        final ArgumentCaptor<OptableAttributes> attributesCaptor = ArgumentCaptor.forClass(OptableAttributes.class);
        verify(optableTargeting).getTargeting(any(), any(), attributesCaptor.capture(), any());
        assertThat(attributesCaptor.getValue())
                .returns(true, OptableAttributes::isGdprApplies)
                .returns("consent", OptableAttributes::getGdprConsent);
    }

    @Test
    void callShouldNotResolveGdprAttributesWhenConsentIsNotValid() {
        // given
        final ModuleContext moduleContext = new ModuleContext();
        moduleContext.setEarlyCallInitializationCompleted(false);

        final TcfContext tcfContext = TcfContext.builder()
                .inGdprScope(true)
                .consentString("invalidConsent")
                .consentValid(false)
                .build();
        final BidRequest bidRequest = givenBidRequest(bidRequestCustomizer -> bidRequestCustomizer
                .user(User.builder().consent("invalidConsent").build())
                .regs(Regs.builder().gdpr(1).build()));
        when(invocationContext.moduleContext()).thenReturn(moduleContext);
        givenAuctionContextWithTcfContext(tcfContext, bidRequest);
        when(optableTargeting.getTargeting(any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture(givenTargetingResult()));
        when(bidderEnrichmentSampler.sample(any(), any())).thenReturn(Set.of("bidder"));

        // when
        target.call(auctionRequestPayload, invocationContext);

        // then
        final ArgumentCaptor<OptableAttributes> attributesCaptor = ArgumentCaptor.forClass(OptableAttributes.class);
        verify(optableTargeting).getTargeting(any(), any(), attributesCaptor.capture(), any());
        assertThat(attributesCaptor.getValue())
                .returns(false, OptableAttributes::isGdprApplies)
                .returns(null, OptableAttributes::getGdprConsent);
    }

    @Test
    void callShouldCleanRequestWhenNoBiddersToEnrich() {
        // given
        final ModuleContext moduleContext = new ModuleContext();
        moduleContext.setEarlyCallInitializationCompleted(false);

        when(invocationContext.moduleContext()).thenReturn(moduleContext);
        when(bidderEnrichmentSampler.sample(any(), any())).thenReturn(Set.of());

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target.call(auctionRequestPayload,
                invocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AuctionRequestPayload> result = future.result();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.update, InvocationResult::action)
                .extracting(InvocationResult::errors).isNull();
        assertThat(result.payloadUpdate()).isNotNull();
        final ObjectNode optable = (ObjectNode) result.payloadUpdate()
                .apply(AuctionRequestPayloadImpl.of(givenBidRequest()))
                .bidRequest()
                .getUser().getExt().getProperty("optable");
        assertThat(optable).isNull();
        assertThat(moduleContext.getOptableTargetingCall()).isNull();
        assertThat(moduleContext.isEarlyCallInitializationCompleted()).isFalse();
    }

    @Test
    void callShouldSkipEnrichmentAndCleanRequestWhenTrafficSourceIsInvalid() {
        // given
        final ModuleContext moduleContext = new ModuleContext();
        moduleContext.setEarlyCallInitializationCompleted(false);

        final BidRequest bidRequestWithoutTrafficSource = givenBidRequest(
                bidRequestCustomizer -> bidRequestCustomizer.site(null).app(null));
        when(invocationContext.moduleContext()).thenReturn(moduleContext);
        when(invocationContext.auctionContext()).thenReturn(
                givenAuctionContext(activityInfrastructure, timeout, Account.builder().id("accountId").build())
                        .toBuilder()
                        .bidRequest(bidRequestWithoutTrafficSource)
                        .build());

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target.call(auctionRequestPayload,
                invocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AuctionRequestPayload> result = future.result();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.update, InvocationResult::action)
                .extracting(InvocationResult::errors).isNull();
        assertThat(result.payloadUpdate()).isNotNull();
        assertThat(moduleContext.isShouldSkipEnrichment()).isTrue();
        assertThat(moduleContext.isEarlyCallInitializationCompleted()).isFalse();
        assertThat(moduleContext.getBiddersToEnrich()).isNull();
        assertThat(moduleContext.getOptableTargetingCall()).isNull();

        final BidRequest bidRequest = result.payloadUpdate()
                .apply(AuctionRequestPayloadImpl.of(givenBidRequest()))
                .bidRequest();
        assertThat(bidRequest.getUser().getExt().getProperty("optable")).isNull();
    }

    @Test
    void callShouldRetryEarlyNetworkCallInitializationWhenResolvedRequestStillHasStoredImps() {
        // given
        final ModuleContext moduleContext = new ModuleContext();
        moduleContext.setEarlyCallInitializationCompleted(false);

        final TargetingResult targetingResult = givenTargetingResult();
        final BidRequest bidRequestWithResolvedStoredImp = givenBidRequest(
                request -> request.imp(List.of(givenImp(imp -> imp.ext(givenResolvedStoredImpExt("bidderA"))))));
        when(invocationContext.moduleContext()).thenReturn(moduleContext);
        when(invocationContext.auctionContext()).thenReturn(
                givenAuctionContext(activityInfrastructure, timeout, Account.builder().id("accountId").build())
                        .toBuilder()
                        .bidRequest(bidRequestWithResolvedStoredImp)
                        .build());
        when(auctionRequestPayload.bidRequest()).thenReturn(bidRequestWithResolvedStoredImp);
        when(optableTargeting.getTargeting(any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture(targetingResult));
        when(bidderEnrichmentSampler.sample(any(), any())).thenReturn(Set.of("bidder"));

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target.call(auctionRequestPayload,
                invocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AuctionRequestPayload> result = future.result();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.update, InvocationResult::action)
                .extracting(InvocationResult::errors).isNull();
        assertThat(moduleContext.getBiddersToEnrich()).containsExactly("bidder");
        assertThat(moduleContext.getOptableTargetingCall()).isNotNull();
        assertThat(moduleContext.getOptableTargetingCall().succeeded()).isTrue();
        assertThat(moduleContext.getOptableTargetingCall().result()).isSameAs(targetingResult);
    }

    private ObjectNode givenAccountConfig(boolean cacheEnabled) {
        return givenAccountConfig("key", "tenant", "origin", cacheEnabled);
    }

    private void givenAuctionContextWithTcfContext(TcfContext tcfContext, BidRequest bidRequest) {
        when(invocationContext.auctionContext()).thenReturn(
                givenAuctionContext(activityInfrastructure, timeout, Account.builder().id("accountId").build())
                        .toBuilder()
                        .bidRequest(bidRequest)
                        .privacyContext(PrivacyContext.of(Privacy.builder().build(), tcfContext, "8.8.8.8"))
                        .build());
        when(auctionRequestPayload.bidRequest()).thenReturn(bidRequest);
    }
}
