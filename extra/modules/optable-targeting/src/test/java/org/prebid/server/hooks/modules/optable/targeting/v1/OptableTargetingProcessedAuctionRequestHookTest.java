package org.prebid.server.hooks.modules.optable.targeting.v1;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Segment;
import com.iab.openrtb.request.Uid;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdActivityMask;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.execution.timeout.TimeoutFactory;
import org.prebid.server.hooks.execution.model.EndpointExecutionPlan;
import org.prebid.server.hooks.execution.model.ExecutionGroup;
import org.prebid.server.hooks.execution.model.ExecutionPlan;
import org.prebid.server.hooks.execution.model.HookHttpEndpoint;
import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionPlan;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.Status;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.BidderEnrichmentSampler;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.CompositeHookExecutionPlan;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.ConfigResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableTargetingFlowResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.TargetingRequestExecutor;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableTargeting;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.settings.model.Account;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OptableTargetingProcessedAuctionRequestHookTest extends BaseOptableTest {

    private ConfigResolver configResolver;

    @Mock
    private OptableTargeting optableTargeting;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private UserFpdActivityMask userFpdActivityMask;

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

    private OptableTargetingProcessedAuctionRequestHook target;

    @BeforeEach
    void setUp() {
        when(userFpdActivityMask.maskDevice(any(), anyBoolean(), anyBoolean()))
                .thenAnswer(answer -> answer.getArgument(0));
        configResolver = new ConfigResolver(mapper, jsonMerger, givenOptableTargetingProperties(false));
        targetingRequestExecutor = new TargetingRequestExecutor(
                optableTargeting, userFpdActivityMask, timeoutFactory, 0.01);
        target = new OptableTargetingProcessedAuctionRequestHook(
                configResolver, givenFlowResolver(ExecutionPlan.empty()));

        when(invocationContext.accountConfig()).thenReturn(givenAccountConfig(true));
        when(invocationContext.auctionContext()).thenReturn(
                givenAuctionContext(activityInfrastructure, timeout, Account.builder().id("accountId").build()));
        when(invocationContext.timeout()).thenReturn(timeout);
        when(activityInfrastructure.isAllowed(any(), any())).thenReturn(true);
        when(timeout.remaining()).thenReturn(1000L);
    }

    private OptableTargetingFlowResolver givenFlowResolver(ExecutionPlan executionPlan) {
        return new OptableTargetingFlowResolver(
                bidderEnrichmentSampler,
                targetingRequestExecutor,
                CompositeHookExecutionPlan.of(executionPlan),
                0.01);
    }

    @Test
    void codeShouldReturnRightCode() {
        // when and then
        assertThat(target.code()).isEqualTo("optable-targeting-processed-auction-request-hook");
    }

    @Test
    void callShouldReturnResultWithPBSAnalyticsTags() {
        // given
        when(auctionRequestPayload.bidRequest()).thenReturn(givenBidRequest());
        when(optableTargeting.getTargeting(any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture(givenTargetingResult()));

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
        assertThat(result.analyticsTags().activities().getFirst()
                .results().getFirst().values().get("execution-time")).isNotNull();
    }

    @Test
    void callShouldReturnResultWithUpdateActionWhenOptableTargetingReturnsTargeting() {
        // given
        when(auctionRequestPayload.bidRequest()).thenReturn(givenBidRequest());
        when(optableTargeting.getTargeting(any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture(givenTargetingResult()));

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
        final BidRequest bidRequest = result
                .payloadUpdate()
                .apply(AuctionRequestPayloadImpl.of(givenBidRequest()))
                .bidRequest();
        assertThat(bidRequest.getUser().getEids())
                .flatExtracting(Eid::getUids)
                .extracting(Uid::getId)
                .containsExactly("id");
        assertThat(bidRequest.getUser().getData())
                .flatExtracting(Data::getSegment)
                .extracting(Segment::getId)
                .containsExactly("id");
    }

    @Test
    void callShouldSetId5SignatureOnModuleContextWhenTargetingResultContainsId5Signature() {
        // given
        final String refValue = "refValue";
        final String signature = "id5Signature";
        final Eid id5Eid = givenId5Eid(refValue);
        final ObjectNode refs = givenRefsObject(refValue, signature);
        when(auctionRequestPayload.bidRequest()).thenReturn(givenBidRequest());
        when(optableTargeting.getTargeting(any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture(givenTargetingResult(List.of(id5Eid), null, refs)));

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target.call(auctionRequestPayload,
                invocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();
        assertThat((ModuleContext) future.result().moduleContext())
                .isNotNull()
                .extracting(ModuleContext::getId5Signature)
                .isEqualTo(signature);
    }

    @Test
    void callShouldLeaveId5SignatureNullWhenTargetingResultHasNoId5Signature() {
        // given
        when(auctionRequestPayload.bidRequest()).thenReturn(givenBidRequest());
        when(optableTargeting.getTargeting(any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture(givenTargetingResult()));

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target.call(auctionRequestPayload,
                invocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();
        assertThat((ModuleContext) future.result().moduleContext())
                .isNotNull()
                .extracting(ModuleContext::getId5Signature)
                .isNull();
    }

    @Test
    void callShouldReturnResultWithNoActionWhenEarlyOptableCallIsEnabledAndInitialized() {
        // given
        final ModuleContext moduleContext = new ModuleContext();
        moduleContext.setEarlyNetworkCallEnabled(true);
        moduleContext.setEarlyCallInitializationCompleted(true);
        target = new OptableTargetingProcessedAuctionRequestHook(
                configResolver,
                givenFlowResolver(givenExecutionPlan(true, false)));
        when(optableTargeting.getTargeting(any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture(givenTargetingResult()));
        when(invocationContext.moduleContext()).thenReturn(moduleContext);
        when(auctionRequestPayload.bidRequest()).thenReturn(givenBidRequest());
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
        moduleContext.setEarlyNetworkCallEnabled(true);
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
    void callShouldNotRetryEarlyNetworkCallInitializationWhenNoBiddersToEnrich() {
        // given
        final ModuleContext moduleContext = new ModuleContext();
        moduleContext.setEarlyNetworkCallEnabled(true);
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
        assertThat(moduleContext.getOptableTargetingCall()).isNull();
        assertThat(moduleContext.isEarlyCallInitializationCompleted()).isFalse();
    }

    @Test
    void callShouldRetryEarlyNetworkCallInitializationWhenResolvedRequestStillHasStoredImps() {
        // given
        final ModuleContext moduleContext = new ModuleContext();
        moduleContext.setEarlyNetworkCallEnabled(true);
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

    @Test
    void callShouldReturnResultWithEnrichedBidRequestWhenBothHooksAreAbsent() {
        // given
        target = new OptableTargetingProcessedAuctionRequestHook(
                configResolver,
                givenFlowResolver(givenExecutionPlan(false, false)));
        when(auctionRequestPayload.bidRequest()).thenReturn(givenBidRequest());
        when(optableTargeting.getTargeting(any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture(givenTargetingResult()));

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
        final BidRequest bidRequest = result
                .payloadUpdate()
                .apply(AuctionRequestPayloadImpl.of(givenBidRequest()))
                .bidRequest();
        assertThat(bidRequest.getUser().getEids())
                .flatExtracting(Eid::getUids)
                .extracting(Uid::getId)
                .containsExactly("id");
        assertThat(bidRequest.getUser().getData())
                .flatExtracting(Data::getSegment)
                .extracting(Segment::getId)
                .containsExactly("id");
    }

    @Test
    void callShouldReturnResultWithEnrichedBidRequestWhenOnlyBidderRequestHookIsPresent() {
        // given
        target = new OptableTargetingProcessedAuctionRequestHook(
                configResolver,
                givenFlowResolver(givenExecutionPlan(false, true)));
        when(auctionRequestPayload.bidRequest()).thenReturn(givenBidRequest());
        when(optableTargeting.getTargeting(any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture(givenTargetingResult()));

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
        final BidRequest bidRequest = result
                .payloadUpdate()
                .apply(AuctionRequestPayloadImpl.of(givenBidRequest()))
                .bidRequest();
        assertThat(bidRequest.getUser().getEids())
                .flatExtracting(Eid::getUids)
                .extracting(Uid::getId)
                .containsExactly("id");
        assertThat(bidRequest.getUser().getData())
                .flatExtracting(Data::getSegment)
                .extracting(Segment::getId)
                .containsExactly("id");
    }

    @Test
    void callShouldReturnResultWithoutEnrichedBidRequestWhenBothHooksArePresent() {
        // given
        target = new OptableTargetingProcessedAuctionRequestHook(
                configResolver,
                givenFlowResolver(givenExecutionPlan(true, true)));
        when(invocationContext.moduleContext()).thenReturn(new ModuleContext());

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
        final BidRequest bidRequest = result
                .payloadUpdate()
                .apply(AuctionRequestPayloadImpl.of(givenBidRequest()))
                .bidRequest();
        assertThat(bidRequest.getUser().getEids()).isNull();
        assertThat(bidRequest.getUser().getData()).isNull();
    }

    @Test
    void callShouldReturnFailWhenOriginIsAbsentInAccountConfiguration() {
        // given
        configResolver = new ConfigResolver(
                mapper,
                jsonMerger,
                givenOptableTargetingProperties("key", "tenant", null, false));
        target = new OptableTargetingProcessedAuctionRequestHook(
                configResolver, givenFlowResolver(ExecutionPlan.empty()));
        when(invocationContext.accountConfig())
                .thenReturn(givenAccountConfig("key", "tenant", null, true));

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target.call(auctionRequestPayload,
                invocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AuctionRequestPayload> result = future.result();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.update, InvocationResult::action);
        assertThat((ModuleContext) result.moduleContext())
                .extracting(it -> it.getEnrichRequestStatus().getStatus())
                .isEqualTo(Status.FAIL);
    }

    @Test
    void callShouldReturnFailWhenTenantIsAbsentInAccountConfiguration() {
        // given
        configResolver = new ConfigResolver(
                mapper,
                jsonMerger,
                givenOptableTargetingProperties("key", null, "origin", false));
        target = new OptableTargetingProcessedAuctionRequestHook(
                configResolver, givenFlowResolver(ExecutionPlan.empty()));
        when(invocationContext.accountConfig())
                .thenReturn(givenAccountConfig("key", null, null, true));

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target.call(auctionRequestPayload,
                invocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AuctionRequestPayload> result = future.result();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.update, InvocationResult::action);
        assertThat((ModuleContext) result.moduleContext())
                .extracting(it -> it.getEnrichRequestStatus().getStatus())
                .isEqualTo(Status.FAIL);
    }

    @Test
    void callShouldReturnResultWithCleanedUpUserExtOptableTag() {
        // given
        when(auctionRequestPayload.bidRequest()).thenReturn(givenBidRequest());
        when(optableTargeting.getTargeting(any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture(givenTargetingResult()));

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
        final ObjectNode optable = (ObjectNode) result
                .payloadUpdate()
                .apply(AuctionRequestPayloadImpl.of(givenBidRequest()))
                .bidRequest()
                .getUser().getExt().getProperty("optable");

        assertThat(optable).isNull();
    }

    @Test
    void callShouldReturnResultWithUpdateWhenOptableTargetingDoesNotReturnResult() {
        // given
        when(auctionRequestPayload.bidRequest()).thenReturn(givenBidRequest());
        when(optableTargeting.getTargeting(any(), any(), any(), any())).thenReturn(Future.succeededFuture(null));

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
    }

    @Test
    void callShouldReturnUpdateWhenTrafficSourceIsInvalid() {
        // given
        final ModuleContext moduleContext = new ModuleContext();
        moduleContext.setShouldSkipEnrichment(true);
        when(invocationContext.moduleContext()).thenReturn(moduleContext);

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
    }

    private ObjectNode givenAccountConfig(boolean cacheEnabled) {
        return givenAccountConfig("key", "tenant", "origin", cacheEnabled);
    }

    private ExecutionPlan givenExecutionPlan(boolean hasRawAuctionRequestHook, boolean hasBidderRequestHook) {
        final HookId rawAuctionHook = HookId.of("optable-targeting", "optable-targeting-raw-auction-request-hook");
        final HookId bidderRequestHook = HookId.of("optable-targeting", "optable-targeting-bidder-request-hook");

        final StageExecutionPlan rawAuctionStage = StageExecutionPlan.of(List.of(
                ExecutionGroup.of(null, hasRawAuctionRequestHook ? List.of(rawAuctionHook) : List.of())
        ));
        final StageExecutionPlan bidderRequestStage = StageExecutionPlan.of(List.of(
                ExecutionGroup.of(null, hasBidderRequestHook ? List.of(bidderRequestHook) : List.of())
        ));

        final EndpointExecutionPlan endpointExecutionPlan = EndpointExecutionPlan.of(Map.of(
                Stage.raw_auction_request, rawAuctionStage,
                Stage.bidder_request, bidderRequestStage
        ));

        return ExecutionPlan.of(null, Map.of(HookHttpEndpoint.POST_AUCTION, endpointExecutionPlan));
    }
}
