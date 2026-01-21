package org.prebid.server.hooks.modules.id5.userid.v1;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Uid;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.execution.timeout.TimeoutFactory;
import org.prebid.server.hooks.execution.v1.InvocationContextImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionInvocationContextImpl;
import org.prebid.server.hooks.execution.v1.bidder.BidderInvocationContextImpl;
import org.prebid.server.hooks.execution.v1.bidder.BidderRequestPayloadImpl;
import org.prebid.server.hooks.modules.id5.userid.v1.filter.FilterResult;
import org.prebid.server.hooks.modules.id5.userid.v1.filter.InjectActionFilter;
import org.prebid.server.hooks.modules.id5.userid.v1.model.Id5UserId;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;
import org.prebid.server.model.Endpoint;
import org.prebid.server.settings.model.Account;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

class Id5IdInjectHookTest {

    private BidderInvocationContextImpl bidderCtxWithEmptyIds() {
        final Timeout timeout = new TimeoutFactory(Clock.systemUTC()).create(1000);
        final AuctionInvocationContext auctionCtx = AuctionInvocationContextImpl.of(
                InvocationContextImpl.of(timeout, Endpoint.openrtb2_auction),
                AuctionContext.builder().account(Account.builder().id("acc").build()).build(),
                false,
                null,
                // provide module context with empty Ids future to ensure fetch path would continue if not filtered
                new Id5IdModuleContext(Future.succeededFuture(Id5UserId.empty())));
        return BidderInvocationContextImpl.of(auctionCtx, "appnexus");
    }

    @Test
    void shouldSkipWhenId5EidAlreadyPresent() {
        // given
        final Id5IdInjectHook hook = new Id5IdInjectHook("inserterX");

        final User userWithId5 = User.builder()
                .eids(List.of(Eid.builder()
                        .source("id5-sync.com")
                        .uids(List.of(Uid.builder().id("abc").build()))
                        .build()))
                .build();
        final BidRequest bidRequest = BidRequest.builder().user(userWithId5).build();

        final Timeout timeout = new TimeoutFactory(Clock.systemUTC()).create(1000);
        final Id5IdModuleContext expectedContext = new Id5IdModuleContext(Future.succeededFuture(Id5UserId.empty()));
        final AuctionInvocationContext auctionCtx = AuctionInvocationContextImpl.of(
                InvocationContextImpl.of(timeout, Endpoint.openrtb2_auction),
                AuctionContext.builder().account(Account.builder().id("acc").build()).build(),
                false,
                null,
                expectedContext);
        final BidderInvocationContext bidderCtx = BidderInvocationContextImpl.of(auctionCtx, "appnexus");

        // when
        final InvocationResult<BidderRequestPayload> result = hook.call(BidderRequestPayloadImpl.of(bidRequest),
                bidderCtx).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_invocation);
        assertThat(result.moduleContext()).isEqualTo(expectedContext);
    }

    @Test
    void shouldSkipWhenNoTimeLeft() {
        // given
        final Id5IdInjectHook hook = new Id5IdInjectHook("inserterX");

        final BidRequest bidRequest = BidRequest.builder().user(User.builder().build()).build();

        final Timeout timeout = new TimeoutFactory(Clock.systemUTC()).create(1).minus(10_000);
        final Id5IdModuleContext expectedContext = new Id5IdModuleContext(Future.succeededFuture(Id5UserId.empty()));
        final AuctionInvocationContext auctionCtx = AuctionInvocationContextImpl.of(
                InvocationContextImpl.of(timeout, Endpoint.openrtb2_auction),
                AuctionContext.builder().account(Account.builder().id("acc").build()).build(),
                false,
                null,
                expectedContext);
        final BidderInvocationContext bidderCtx = BidderInvocationContextImpl.of(auctionCtx, "appnexus");

        // when
        final InvocationResult<BidderRequestPayload> result = hook.call(BidderRequestPayloadImpl.of(bidRequest),
                bidderCtx).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_invocation);
        assertThat(result.moduleContext()).isEqualTo(expectedContext);
    }

    @Test
    void shouldSkipWhenFetcherReturnsEmpty() {
        // given
        final Id5IdInjectHook hook = new Id5IdInjectHook("inserterX");

        final BidRequest bidRequest = BidRequest.builder().user(User.builder().build()).build();

        final Timeout timeout = new TimeoutFactory(Clock.systemUTC()).create(1000);
        final Id5IdModuleContext expectedContext = new Id5IdModuleContext(Future.succeededFuture(Id5UserId.empty()));
        final AuctionInvocationContext auctionCtx = AuctionInvocationContextImpl.of(
                InvocationContextImpl.of(timeout, Endpoint.openrtb2_auction),
                AuctionContext.builder().account(Account.builder().id("acc").build()).build(),
                false,
                null,
                expectedContext);
        final BidderInvocationContext bidderCtx = BidderInvocationContextImpl.of(auctionCtx, "appnexus");

        // when
        final InvocationResult<BidderRequestPayload> result = hook.call(BidderRequestPayloadImpl.of(bidRequest),
                bidderCtx).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
        assertThat(result.moduleContext()).isEqualTo(expectedContext);
    }

    @Test
    void shouldInjectEidsWhenFetcherReturnsIds() {
        // given
        final Id5IdInjectHook hook = new Id5IdInjectHook("inserterX");

        final BidRequest bidRequest = BidRequest.builder().user(User.builder().eids(List.of()).build()).build();

        final Id5UserId id5 = () -> List.of(
                Eid.builder()
                        .source("id5-sync.com")
                        .uids(List.of(Uid.builder().id("id5-123").build()))
                        .build());

        final Timeout timeout = new TimeoutFactory(Clock.systemUTC()).create(1000);
        final Id5IdModuleContext expectedContext = new Id5IdModuleContext(Future.succeededFuture(id5));
        final AuctionInvocationContext auctionCtx = AuctionInvocationContextImpl.of(
                InvocationContextImpl.of(timeout, Endpoint.openrtb2_auction),
                AuctionContext.builder().account(Account.builder().id("acc").build()).build(),
                false,
                null,
                expectedContext);
        final BidderInvocationContext bidderCtx = BidderInvocationContextImpl.of(auctionCtx, "appnexus");

        // when
        final InvocationResult<BidderRequestPayload> result = hook.call(BidderRequestPayloadImpl.of(bidRequest),
                        bidderCtx)
                .toCompletionStage().toCompletableFuture().join();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
        assertThat(result.moduleContext()).isEqualTo(expectedContext);

        final BidderRequestPayload updated = result.payloadUpdate().apply(BidderRequestPayloadImpl.of(bidRequest));
        assertThat(updated.bidRequest().getUser().getEids()).hasSize(1);
        final Eid eid = updated.bidRequest().getUser().getEids().getFirst();
        assertThat(eid.getSource()).isEqualTo("id5-sync.com");
        assertThat(eid.getInserter()).isEqualTo("inserterX");
    }

    @Test
    void shouldReturnNoActionWhenNoModuleContextPresent() {
        // given
        final Id5IdInjectHook hook = new Id5IdInjectHook("inserterX");

        final BidRequest bidRequest = BidRequest.builder().user(User.builder().build()).build();

        final Timeout timeout = new TimeoutFactory(Clock.systemUTC()).create(1000);
        final AuctionInvocationContext auctionCtx = AuctionInvocationContextImpl.of(
                InvocationContextImpl.of(timeout, Endpoint.openrtb2_auction),
                AuctionContext.builder().account(Account.builder().id("acc").build()).build(),
                false,
                null,
                null // no Id5IdModuleContext provided
        );
        final BidderInvocationContext bidderCtx = BidderInvocationContextImpl.of(auctionCtx, "appnexus");

        // when
        final InvocationResult<BidderRequestPayload> result = hook.call(BidderRequestPayloadImpl.of(bidRequest),
                bidderCtx).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
        assertThat(result.payloadUpdate()).isNull();
        assertThat(result.moduleContext()).isNull(); // nothing to propagate
    }

    @Test
    void shouldReturnNoInvocationWhenInjectFilterRejectsSingleFilter() {
        // given
        final InjectActionFilter filter = Mockito.mock(InjectActionFilter.class);
        Mockito.when(filter.shouldInvoke(any(), any())).thenReturn(FilterResult.rejected("reject-by-filter"));

        final Id5IdInjectHook hook = new Id5IdInjectHook("inserterX", List.of(filter));

        final BidRequest bidRequest = BidRequest.builder().build();
        final BidderInvocationContext bidderCtx = bidderCtxWithEmptyIds();

        // when
        final InvocationResult<BidderRequestPayload> result = hook.call(BidderRequestPayloadImpl.of(bidRequest),
                bidderCtx).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_invocation);
        assertThat(result.payloadUpdate()).isNull();
        assertThat(result.debugMessages()).anyMatch(m -> m.contains("reject-by-filter"));
        assertThat(result.moduleContext()).isNotNull();
        assertThat(result.moduleContext()).isInstanceOf(Id5IdModuleContext.class);
        assertThat(result.moduleContext()).isEqualTo(bidderCtx.moduleContext());
    }

    @Test
    void shouldReturnNoInvocationWhenAnyInjectFilterRejectsMultipleFilters() {
        // given
        final InjectActionFilter accept1 = Mockito.mock(InjectActionFilter.class);
        final InjectActionFilter reject = Mockito.mock(InjectActionFilter.class);
        final InjectActionFilter accept2 = Mockito.mock(InjectActionFilter.class);
        Mockito.when(accept1.shouldInvoke(any(), any())).thenReturn(FilterResult.accepted());
        Mockito.when(reject.shouldInvoke(any(), any())).thenReturn(FilterResult.rejected("block-by-second"));
        Mockito.when(accept2.shouldInvoke(any(), any())).thenReturn(FilterResult.accepted());

        final Id5IdInjectHook hook = new Id5IdInjectHook("inserterX", List.of(accept1, reject, accept2));

        final BidRequest bidRequest = BidRequest.builder().build();
        final BidderInvocationContext bidderCtx = bidderCtxWithEmptyIds();

        // when
        final InvocationResult<BidderRequestPayload> result = hook.call(BidderRequestPayloadImpl.of(bidRequest),
                bidderCtx).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_invocation);
        assertThat(result.payloadUpdate()).isNull();
        assertThat(result.debugMessages()).anyMatch(m -> m.contains("block-by-second"));
        assertThat(result.moduleContext()).isNotNull();
        assertThat(result.moduleContext()).isInstanceOf(Id5IdModuleContext.class);
        assertThat(result.moduleContext()).isEqualTo(bidderCtx.moduleContext());
    }

    @Test
    void shouldReturnFailureWhenExceptionOccurs() {
        // given
        final InjectActionFilter filter = Mockito.mock(InjectActionFilter.class);
        Mockito.when(filter.shouldInvoke(any(), any()))
                .thenThrow(new RuntimeException("Filter processing error"));

        final Id5IdInjectHook hook = new Id5IdInjectHook("inserterX", List.of(filter));

        final BidRequest bidRequest = BidRequest.builder().build();
        final BidderInvocationContextImpl invocationContext = bidderCtxWithEmptyIds();

        // when
        final InvocationResult<BidderRequestPayload> result = hook.call(BidderRequestPayloadImpl.of(bidRequest),
                invocationContext).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.failure);
        assertThat(result.action()).isEqualTo(InvocationAction.no_invocation);
        assertThat(result.moduleContext()).isEqualTo(invocationContext.moduleContext());
        assertThat(result.errors()).isNotEmpty();
        assertThat(result.errors().getFirst()).contains("Filter processing error");
    }
}
