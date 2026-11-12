package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.execution.timeout.TimeoutFactory;
import org.prebid.server.hooks.execution.model.ExecutionPlan;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.v1.BaseOptableTest;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.settings.model.Account;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OptableTargetingFlowExecutorTest extends BaseOptableTest {

    @Mock
    private BidderEnrichmentSampler bidderEnrichmentSampler;

    @Mock
    private TargetingRequestExecutor targetingRequestExecutor;

    @Mock
    private AuctionRequestPayload auctionRequestPayload;

    @Mock
    private AuctionInvocationContext invocationContext;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private TimeoutFactory timeoutFactory;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private Timeout timeout;

    private OptableTargetingFlowExecutor target;

    @BeforeEach
    public void setUp() {
        target = new OptableTargetingFlowExecutor(
                bidderEnrichmentSampler,
                targetingRequestExecutor,
                CompositeHookExecutionPlan.of(ExecutionPlan.empty(), null),
                timeoutFactory,
                0.01);
    }

    @Test
    public void executeAsyncOptableTargetingFlowShouldProceedWhenBidRequestIsValid() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request.imp(List.of(
                givenImp(imp -> imp.ext(givenPrebidBidderExt("bidderA"))))));
        givenInvocationContext(bidRequest);
        final Future<TargetingResult> targetingResultFuture = Future.succeededFuture(givenTargetingResult());
        when(bidderEnrichmentSampler.sample(any(), any())).thenReturn(Set.of("bidder"));
        when(targetingRequestExecutor.makeRequest(any(), any(), any(), any())).thenReturn(targetingResultFuture);
        when(invocationContext.timeout()).thenReturn(timeout);
        when(timeout.remaining()).thenReturn(1000L);
        final ModuleContext moduleContext = new ModuleContext();

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target.executeAsyncOptableTargetingFlow(
                moduleContext, auctionRequestPayload, invocationContext, givenOptableTargetingProperties(false));

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
        assertThat(moduleContext.isEarlyCallInitializationCompleted()).isFalse();
    }

    @Test
    public void executeAsyncOptableTargetingFlowShouldCleanRequestWhenNoBiddersToEnrich() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request.imp(List.of(
                givenImp(imp -> imp.ext(givenPrebidBidderExt("bidderA"))))));
        givenInvocationContext(bidRequest);
        when(bidderEnrichmentSampler.sample(any(), any())).thenReturn(Set.of());
        final ModuleContext moduleContext = new ModuleContext();

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target.executeAsyncOptableTargetingFlow(
                moduleContext, auctionRequestPayload, invocationContext, givenOptableTargetingProperties(false));

        // then
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AuctionRequestPayload> result = future.result();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.update, InvocationResult::action)
                .extracting(InvocationResult::errors).isNull();
        assertThat(result.payloadUpdate()).isNotNull();
        assertThat(moduleContext.getBiddersToEnrich()).isNull();
        assertThat(moduleContext.getOptableTargetingCall()).isNull();
        assertThat(moduleContext.isEarlyCallInitializationCompleted()).isFalse();

        final AuctionRequestPayload cleanedPayload = result.payloadUpdate()
                .apply(AuctionRequestPayloadImpl.of(givenBidRequestWithUser(givenUser())));
        assertThat(cleanedPayload.bidRequest().getUser().getExt().getProperty("optable")).isNull();
    }

    private void givenInvocationContext(BidRequest bidRequest) {
        when(invocationContext.auctionContext()).thenReturn(
                AuctionContext.builder()
                        .bidRequest(bidRequest)
                        .account(Account.builder().id("accountId").build())
                        .build());
    }
}
