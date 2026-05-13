package org.prebid.server.hooks.modules.optable.targeting.v1;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdActivityMask;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.Status;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.ConfigResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.NetworkCall;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableTargeting;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class OptableTargetingProcessedAuctionRequestHookTest extends BaseOptableTest {

    private ConfigResolver configResolver;

    @Mock
    private OptableTargeting optableTargeting;

    @Mock
    private UserFpdActivityMask userFpdActivityMask;

    @Mock
    private AuctionRequestPayload auctionRequestPayload;

    @Mock
    private AuctionInvocationContext invocationContext;

    @Mock
    private ActivityInfrastructure activityInfrastructure;

    @Mock
    private Timeout timeout;

    private NetworkCall networkCall;

    private OptableTargetingProcessedAuctionRequestHook target;

    @BeforeEach
    public void setUp() {
        when(userFpdActivityMask.maskDevice(any(), anyBoolean(), anyBoolean()))
                .thenAnswer(answer -> answer.getArgument(0));
        configResolver = new ConfigResolver(mapper, jsonMerger, givenOptableTargetingProperties(false));
        networkCall = new NetworkCall(optableTargeting, userFpdActivityMask);
        target = new OptableTargetingProcessedAuctionRequestHook(configResolver, networkCall, 0.01);

        when(invocationContext.accountConfig()).thenReturn(givenAccountConfig(true));
        when(invocationContext.auctionContext()).thenReturn(givenAuctionContext(activityInfrastructure, timeout));
        when(invocationContext.timeout()).thenReturn(timeout);
        when(activityInfrastructure.isAllowed(any(), any())).thenReturn(true);
        when(timeout.remaining()).thenReturn(1000L);
    }

    @Test
    public void shouldHaveRightCode() {
        // when and then
        assertThat(target.code()).isEqualTo("optable-targeting-processed-auction-request-hook");
    }

    @Test
    public void shouldReturnResultWithPBSAnalyticsTags() {
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
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
        assertThat(result.errors()).isNull();
        assertThat(result.analyticsTags().activities().getFirst()
                .results().getFirst().values().get("execution-time")).isNotNull();
    }

    @Test
    public void shouldReturnResultWithUpdateActionWhenOptableTargetingReturnTargeting() {
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
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
        assertThat(result.errors()).isNull();
        final BidRequest bidRequest = result
                .payloadUpdate()
                .apply(AuctionRequestPayloadImpl.of(givenBidRequest()))
                .bidRequest();
        assertThat(bidRequest.getUser().getEids().getFirst().getUids().getFirst().getId()).isEqualTo("id");
        assertThat(bidRequest.getUser().getData().getFirst().getSegment().getFirst().getId()).isEqualTo("id");
    }

    @Test
    public void shouldReturnResultWithUpdateActionWhenEarlyOptableCallIsEnabled() {
        // given
        final ModuleContext moduleContext = new ModuleContext();
        moduleContext.setEarlyNetworkCallEnabled(true);
        when(optableTargeting.getTargeting(any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture(givenTargetingResult()));
        when(invocationContext.moduleContext()).thenReturn(moduleContext);
        when(auctionRequestPayload.bidRequest()).thenReturn(givenBidRequest());
        moduleContext.setOptableTargetingCall(
                networkCall.makeRequest(auctionRequestPayload, invocationContext, givenOptableTargetingProperties(
                        "key", "tenant", "origin", false)));

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target.call(auctionRequestPayload,
                invocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AuctionRequestPayload> result = future.result();
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
        assertThat(result.errors()).isNull();
        final BidRequest bidRequest = result
                .payloadUpdate()
                .apply(AuctionRequestPayloadImpl.of(givenBidRequest()))
                .bidRequest();
        assertThat(bidRequest.getUser().getEids().getFirst().getUids().getFirst().getId()).isEqualTo("id");
        assertThat(bidRequest.getUser().getData().getFirst().getSegment().getFirst().getId()).isEqualTo("id");
    }

    @Test
    public void shouldReturnFailWhenOriginIsAbsentInAccountConfiguration() {
        // given
        configResolver = new ConfigResolver(
                mapper,
                jsonMerger,
                givenOptableTargetingProperties("key", "tenant", null, false));
        target = new OptableTargetingProcessedAuctionRequestHook(configResolver, networkCall, 0.01);
        when(invocationContext.accountConfig())
                .thenReturn(givenAccountConfig("key", "tenant", null, true));

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target.call(auctionRequestPayload,
                invocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AuctionRequestPayload> result = future.result();
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
        assertThat((ModuleContext) result.moduleContext())
                .extracting(it -> it.getEnrichRequestStatus().getStatus())
                .isEqualTo(Status.FAIL);
    }

    @Test
    public void shouldReturnFailWhenTenantIsAbsentInAccountConfiguration() {
        // given
        configResolver = new ConfigResolver(
                mapper,
                jsonMerger,
                givenOptableTargetingProperties("key", null, "origin", false));
        target = new OptableTargetingProcessedAuctionRequestHook(configResolver, networkCall, 0.01);
        when(invocationContext.accountConfig())
                .thenReturn(givenAccountConfig("key", null, null, true));

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target.call(auctionRequestPayload,
                invocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AuctionRequestPayload> result = future.result();
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
        assertThat((ModuleContext) result.moduleContext())
                .extracting(it -> it.getEnrichRequestStatus().getStatus())
                .isEqualTo(Status.FAIL);
    }

    @Test
    public void shouldReturnResultWithCleanedUpUserExtOptableTag() {
        // given
        when(invocationContext.timeout()).thenReturn(timeout);
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
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
        assertThat(result.errors()).isNull();
        final ObjectNode optable = (ObjectNode) result
                .payloadUpdate()
                .apply(AuctionRequestPayloadImpl.of(givenBidRequest()))
                .bidRequest()
                .getUser().getExt().getProperty("optable");

        assertThat(optable).isNull();
    }

    @Test
    public void shouldReturnResultWithUpdateWhenOptableTargetingDoesntReturnResult() {
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
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
        assertThat(result.errors()).isNull();
    }

    @Test
    public void shouldReturnUpdateWhenTrafficSourceIsInvalid() {
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
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
        assertThat(result.errors()).isNull();
    }

    private ObjectNode givenAccountConfig(boolean cacheEnabled) {
        return givenAccountConfig("key", "tenant", "origin", cacheEnabled);
    }
}
