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
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.ConfigResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableAttributesResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableTargeting;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.PayloadResolver;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    private ConfigResolver configResolver;

    private PayloadResolver payloadResolver;

    private OptableAttributesResolver optableAttributesResolver;

    private OptableTargetingProcessedAuctionRequestHook target;

    @BeforeEach
    public void setUp() {
        when(timeout.remaining()).thenReturn(1000L);
        when(invocationContext.accountConfig()).thenReturn(givenAccountConfig(true));
        when(invocationContext.auctionContext()).thenReturn(givenAuctionContext(timeout));
        configResolver = new ConfigResolver(mapper, givenOptableTargetingProperties(false));
        payloadResolver = new PayloadResolver(mapper);
        optableAttributesResolver = new OptableAttributesResolver();
        target = new OptableTargetingProcessedAuctionRequestHook(configResolver, optableTargeting,
                payloadResolver, optableAttributesResolver);
    }

    @Test
    public void shouldHaveRightCode() {
        // when and then
        assertThat(target.code()).isEqualTo("optable-targeting-processed-auction-request-hook");
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
        when(invocationContext.accountConfig()).thenReturn(givenAccountConfig(false));
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
    public void shouldReturnResultWithoutUpdateActionWhenBidRequestIsNull() {
        // given
        when(auctionRequestPayload.bidRequest()).thenReturn(null);
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
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
        assertThat(result.errors()).isNull();
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
