package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.execution.model.ExecutionPlan;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.v1.BaseOptableTest;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.settings.model.Account;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OptableTargetingFlowResolverTest extends BaseOptableTest {

    @Mock
    private BidderEnrichmentSampler bidderEnrichmentSampler;

    @Mock
    private TargetingRequestExecutor targetingRequestExecutor;

    @Mock
    private AuctionRequestPayload auctionRequestPayload;

    @Mock
    private AuctionInvocationContext invocationContext;

    private OptableTargetingFlowResolver target;

    @BeforeEach
    public void setUp() {
        target = new OptableTargetingFlowResolver(
                bidderEnrichmentSampler,
                targetingRequestExecutor,
                CompositeHookExecutionPlan.of(ExecutionPlan.empty(), null),
                0.01);
    }

    @Test
    public void resolveAsyncOptableTargetingFlowShouldProceedWhenBidRequestHasNoStoredRequestAndNoStoredImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request.imp(List.of(
                givenImp(imp -> imp.ext(givenPrebidBidderExt("bidderA"))))));
        givenInvocationContext(bidRequest);
        final Future<TargetingResult> targetingResultFuture = Future.succeededFuture(givenTargetingResult());
        when(bidderEnrichmentSampler.sample(any(), any())).thenReturn(Set.of("bidder"));
        when(targetingRequestExecutor.makeRequest(any(), any(), any(), any())).thenReturn(targetingResultFuture);
        final ModuleContext moduleContext = new ModuleContext();

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target.resolveAsyncOptableTargetingFlow(
                moduleContext, auctionRequestPayload, invocationContext, givenOptableTargetingProperties(false), false);

        // then
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AuctionRequestPayload> result = future.result();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.update, InvocationResult::action)
                .extracting(InvocationResult::errors).isNull();
        assertThat(result.payloadUpdate()).isNotNull();
        assertThat(moduleContext.getBiddersToEnrich()).containsExactly("bidder");
        assertThat(moduleContext.getOptableTargetingCall()).isSameAs(targetingResultFuture);
        assertThat(moduleContext.isEarlyCallInitializationCompleted()).isTrue();
    }

    @Test
    public void resolveAsyncOptableTargetingFlowShouldDeferWhenBidRequestReferencesStoredRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request
                .imp(List.of(givenImp(imp -> imp.ext(givenPrebidBidderExt("bidderA")))))
                .ext(givenStoredRequestExt()));
        givenInvocationContext(bidRequest);
        when(bidderEnrichmentSampler.sample(any(), any())).thenReturn(Set.of("bidder"));
        final ModuleContext moduleContext = new ModuleContext();

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target.resolveAsyncOptableTargetingFlow(
                moduleContext, auctionRequestPayload, invocationContext, givenOptableTargetingProperties(false), false);

        // then
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AuctionRequestPayload> result = future.result();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.no_action, InvocationResult::action)
                .extracting(InvocationResult::errors).isNull();
        assertThat(result.payloadUpdate()).isNull();
        assertThat(moduleContext.isShouldSkipEnrichment()).isFalse();
        assertThat(moduleContext.getBiddersToEnrich()).isNull();
        assertThat(moduleContext.getOptableTargetingCall()).isNull();
        assertThat(moduleContext.isEarlyCallInitializationCompleted()).isFalse();
    }

    @Test
    public void resolveAsyncOptableTargetingFlowShouldDeferWhenAnyImpReferencesStoredImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request.imp(List.of(
                givenImp(imp -> imp.ext(givenPrebidBidderExt("bidderA"))),
                givenImp(imp -> imp.ext(givenStoredImpExt())))));
        givenInvocationContext(bidRequest);
        when(bidderEnrichmentSampler.sample(any(), any())).thenReturn(Set.of("bidder"));
        final ModuleContext moduleContext = new ModuleContext();

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target.resolveAsyncOptableTargetingFlow(
                moduleContext, auctionRequestPayload, invocationContext, givenOptableTargetingProperties(false), false);

        // then
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AuctionRequestPayload> result = future.result();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.no_action, InvocationResult::action)
                .extracting(InvocationResult::errors).isNull();
        assertThat(result.payloadUpdate()).isNull();
        assertThat(moduleContext.isShouldSkipEnrichment()).isFalse();
        assertThat(moduleContext.getBiddersToEnrich()).isNull();
        assertThat(moduleContext.getOptableTargetingCall()).isNull();
        assertThat(moduleContext.isEarlyCallInitializationCompleted()).isFalse();
    }

    @Test
    public void resolveAsyncOptableTargetingFlowShouldDeferWhenNoBiddersToEnrich() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request.imp(List.of(
                givenImp(imp -> imp.ext(givenPrebidBidderExt("bidderA"))))));
        givenInvocationContext(bidRequest);
        when(bidderEnrichmentSampler.sample(any(), any())).thenReturn(Set.of());
        final ModuleContext moduleContext = new ModuleContext();

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target.resolveAsyncOptableTargetingFlow(
                moduleContext, auctionRequestPayload, invocationContext, givenOptableTargetingProperties(false), false);

        // then
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AuctionRequestPayload> result = future.result();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.no_action, InvocationResult::action)
                .extracting(InvocationResult::errors).isNull();
        assertThat(result.payloadUpdate()).isNull();
        assertThat(moduleContext.getOptableTargetingCall()).isNull();
        assertThat(moduleContext.isEarlyCallInitializationCompleted()).isFalse();
    }

    @Test
    public void resolveAsyncOptableTargetingFlowShouldProceedWhenProcessedStageCallHasStoredRequestAndStoredImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request
                .imp(List.of(givenImp(imp -> imp.ext(givenResolvedStoredImpExt("bidderA")))))
                .ext(givenStoredRequestExt()));
        givenInvocationContext(bidRequest);
        final Future<TargetingResult> targetingResultFuture = Future.succeededFuture(givenTargetingResult());
        when(bidderEnrichmentSampler.sample(any(), any())).thenReturn(Set.of("bidder"));
        when(targetingRequestExecutor.makeRequest(any(), any(), any(), any())).thenReturn(targetingResultFuture);
        final ModuleContext moduleContext = new ModuleContext();

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target.resolveAsyncOptableTargetingFlow(
                moduleContext, auctionRequestPayload, invocationContext, givenOptableTargetingProperties(false), true);

        // then
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AuctionRequestPayload> result = future.result();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.update, InvocationResult::action)
                .extracting(InvocationResult::errors).isNull();
        assertThat(result.payloadUpdate()).isNotNull();
        assertThat(moduleContext.getBiddersToEnrich()).containsExactly("bidder");
        assertThat(moduleContext.getOptableTargetingCall()).isSameAs(targetingResultFuture);
        assertThat(moduleContext.isEarlyCallInitializationCompleted()).isTrue();
    }

    private void givenInvocationContext(BidRequest bidRequest) {
        when(invocationContext.auctionContext()).thenReturn(
                AuctionContext.builder()
                        .bidRequest(bidRequest)
                        .account(Account.builder().id("accountId").build())
                        .build());
    }

    private ExtRequest givenStoredRequestExt() {
        return ExtRequest.of(ExtRequestPrebid.builder()
                .storedrequest(ExtStoredRequest.of("storedRequestId"))
                .build());
    }
}
