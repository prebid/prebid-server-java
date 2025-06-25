package org.prebid.server.hooks.modules.optable.targeting.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.prebid.server.hooks.modules.optable.targeting.v1.core.ConfigResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableTargeting;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class OptableTargetingProcessedAuctionRequestHookTest extends BaseOptableTest {

    @Mock
    OptableTargeting optableTargeting;
    @Mock
    AuctionRequestPayload auctionRequestPayload;
    @Mock
    AuctionInvocationContext invocationContext;
    @Mock
    Timeout timeout;
    @Mock
    UserFpdActivityMask userFpdActivityMask;
    @Mock
    ActivityInfrastructure activityInfrastructure;
    private ConfigResolver configResolver;
    private JsonMerger jsonMerger = new JsonMerger(new JacksonMapper(new ObjectMapper()));
    private OptableTargetingProcessedAuctionRequestHook target;

    @BeforeEach
    public void setUp() {
        when(activityInfrastructure.isAllowed(any(), any())).thenReturn(true);
        when(userFpdActivityMask.maskDevice(any(), anyBoolean(), anyBoolean()))
                .thenAnswer(answer -> answer.getArgument(0));
        when(timeout.remaining()).thenReturn(1000L);
        when(invocationContext.accountConfig()).thenReturn(givenAccountConfig(true));
        when(invocationContext.auctionContext()).thenReturn(givenAuctionContext(activityInfrastructure, timeout));
        when(invocationContext.timeout()).thenReturn(timeout);
        configResolver = new ConfigResolver(mapper, jsonMerger, givenOptableTargetingProperties(false));
        target = new OptableTargetingProcessedAuctionRequestHook(
                configResolver,
                optableTargeting,
                userFpdActivityMask,
                jsonMerger);
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
        when(optableTargeting.getTargeting(any(), any(), any(), anyLong()))
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
        when(optableTargeting.getTargeting(any(), any(), any(), anyLong()))
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
    public void shouldReturnResultWithCleanedUpUserExtOptableTag() {
        // given
        when(invocationContext.timeout()).thenReturn(timeout);
        when(auctionRequestPayload.bidRequest()).thenReturn(givenBidRequest());
        when(optableTargeting.getTargeting(any(), any(), any(), anyLong()))
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
        when(optableTargeting.getTargeting(any(), any(), any(), anyLong())).thenReturn(Future.succeededFuture(null));

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
        return mapper.valueToTree(givenOptableTargetingProperties(cacheEnabled));
    }
}
