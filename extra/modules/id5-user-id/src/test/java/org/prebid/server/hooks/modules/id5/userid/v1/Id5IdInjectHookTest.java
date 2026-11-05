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
import org.prebid.server.hooks.modules.id5.userid.v1.filter.InjectFilter;
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

public class Id5IdInjectHookTest {

    @Test
    public void shouldSkipWhenId5EidAlreadyPresent() {
        // given
        final Id5IdInjectHook hook = new Id5IdInjectHook("inserterX", List.of());

        final User userWithId5 = User.builder()
                .eids(List.of(Eid.builder()
                        .source("id5-sync.com")
                        .uids(List.of(Uid.builder().id("abc").build()))
                        .build()))
                .build();
        final BidRequest bidRequest = BidRequest.builder().user(userWithId5).build();

        final Id5IdModuleContext expectedContext = new Id5IdModuleContext(Future.succeededFuture(Id5UserId.empty()));
        final BidderInvocationContext bidderCtx = bidderCtx(expectedContext);

        // when
        final InvocationResult<BidderRequestPayload> result = hook
                .call(BidderRequestPayloadImpl.of(bidRequest), bidderCtx)
                .result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_invocation);
        assertThat(result.moduleContext()).isEqualTo(expectedContext);
    }

    @Test
    public void shouldSkipWhenFetcherReturnsEmpty() {
        // given
        final Id5IdInjectHook hook = new Id5IdInjectHook("inserterX", List.of());

        final BidRequest bidRequest = BidRequest.builder().user(User.builder().build()).build();

        final Id5IdModuleContext expectedContext = new Id5IdModuleContext(Future.succeededFuture(Id5UserId.empty()));
        final BidderInvocationContext bidderCtx = bidderCtx(expectedContext);

        // when
        final InvocationResult<BidderRequestPayload> result = hook
                .call(BidderRequestPayloadImpl.of(bidRequest), bidderCtx)
                .result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
        assertThat(result.moduleContext()).isEqualTo(expectedContext);
    }

    @Test
    public void shouldInjectEidsWhenFetcherReturnsIds() {
        // given
        final Id5IdInjectHook hook = new Id5IdInjectHook("inserterX", List.of());

        final BidRequest bidRequest = BidRequest.builder().user(User.builder().eids(List.of()).build()).build();

        final Id5IdModuleContext expectedContext = new Id5IdModuleContext(Future.succeededFuture(singleId5()));
        final BidderInvocationContext bidderCtx = bidderCtx(expectedContext);

        // when
        final InvocationResult<BidderRequestPayload> result = hook
                .call(BidderRequestPayloadImpl.of(bidRequest), bidderCtx)
                .result();

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
    public void shouldInjectEidsWhenUserIsNull() {
        // when
        final List<String> sources = injectedEidSources(null);

        // then
        assertThat(sources).containsExactlyInAnyOrder("id5-sync.com");
    }

    @Test
    public void shouldInjectEidsWhenUserEidsAreNull() {
        // when
        final List<String> sources = injectedEidSources(User.builder().build());

        // then
        assertThat(sources).containsExactlyInAnyOrder("id5-sync.com");
    }

    @Test
    public void shouldMergeEidsWithExistingNonId5Eids() {
        // given
        final User user = User.builder()
                .eids(List.of(Eid.builder()
                        .source("other-sync.com")
                        .uids(List.of(Uid.builder().id("other-123").build()))
                        .build()))
                .build();

        // when
        final List<String> sources = injectedEidSources(user);

        // then
        assertThat(sources).containsExactlyInAnyOrder("other-sync.com", "id5-sync.com");
    }

    @Test
    public void shouldReturnNoActionWhenNoModuleContextPresent() {
        // given
        final Id5IdInjectHook hook = new Id5IdInjectHook("inserterX", List.of());

        final BidRequest bidRequest = BidRequest.builder().user(User.builder().build()).build();
        final BidderInvocationContext bidderCtx = bidderCtx(null);

        // when
        final InvocationResult<BidderRequestPayload> result = hook
                .call(BidderRequestPayloadImpl.of(bidRequest), bidderCtx)
                .result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
        assertThat(result.payloadUpdate()).isNull();
        assertThat(result.moduleContext()).isNull();
    }

    @Test
    public void shouldReturnNoInvocationWhenInjectFilterRejectsSingleFilter() {
        // given
        final InjectFilter filter = Mockito.mock(InjectFilter.class);
        Mockito.when(filter.shouldInvoke(any(), any())).thenReturn(FilterResult.rejected("reject-by-filter"));

        final Id5IdInjectHook hook = new Id5IdInjectHook("inserterX", List.of(filter));

        final BidRequest bidRequest = BidRequest.builder().build();
        final BidderInvocationContext bidderCtx = bidderCtx(new Id5IdModuleContext(
                Future.succeededFuture(Id5UserId.empty())));

        // when
        final InvocationResult<BidderRequestPayload> result = hook
                .call(BidderRequestPayloadImpl.of(bidRequest), bidderCtx)
                .result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_invocation);
        assertThat(result.payloadUpdate()).isNull();
        assertThat(result.debugMessages()).anyMatch(m -> m.contains("reject-by-filter"));
        assertThat(result.moduleContext()).isInstanceOf(Id5IdModuleContext.class);
        assertThat(result.moduleContext()).isEqualTo(bidderCtx.moduleContext());
    }

    @Test
    public void shouldReturnNoInvocationWhenAnyInjectFilterRejectsMultipleFilters() {
        // given
        final InjectFilter accept1 = Mockito.mock(InjectFilter.class);
        final InjectFilter reject = Mockito.mock(InjectFilter.class);
        final InjectFilter accept2 = Mockito.mock(InjectFilter.class);
        Mockito.when(accept1.shouldInvoke(any(), any())).thenReturn(FilterResult.accepted());
        Mockito.when(reject.shouldInvoke(any(), any())).thenReturn(FilterResult.rejected("block-by-second"));
        Mockito.when(accept2.shouldInvoke(any(), any())).thenReturn(FilterResult.accepted());

        final Id5IdInjectHook hook = new Id5IdInjectHook("inserterX", List.of(accept1, reject, accept2));

        final BidRequest bidRequest = BidRequest.builder().build();
        final BidderInvocationContext bidderCtx = bidderCtx(new Id5IdModuleContext(
                Future.succeededFuture(Id5UserId.empty())));

        // when
        final InvocationResult<BidderRequestPayload> result = hook
                .call(BidderRequestPayloadImpl.of(bidRequest), bidderCtx)
                .result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_invocation);
        assertThat(result.payloadUpdate()).isNull();
        assertThat(result.debugMessages()).anyMatch(m -> m.contains("block-by-second"));
        assertThat(result.moduleContext()).isInstanceOf(Id5IdModuleContext.class);
        assertThat(result.moduleContext()).isEqualTo(bidderCtx.moduleContext());
    }

    private static List<String> injectedEidSources(User user) {
        final Id5IdInjectHook hook = new Id5IdInjectHook(null, List.of());
        final BidRequest bidRequest = BidRequest.builder().user(user).build();

        final InvocationResult<BidderRequestPayload> result = hook
                .call(BidderRequestPayloadImpl.of(bidRequest), bidderCtx(
                        new Id5IdModuleContext(Future.succeededFuture(singleId5()))))
                .result();

        final BidderRequestPayload updated = result.payloadUpdate().apply(BidderRequestPayloadImpl.of(bidRequest));
        return updated.bidRequest().getUser().getEids().stream().map(Eid::getSource).toList();
    }

    private static Id5UserId singleId5() {
        return new Id5UserId(List.of(Eid.builder()
                .source("id5-sync.com")
                .uids(List.of(Uid.builder().id("id5-123").build()))
                .build()));
    }

    private static BidderInvocationContext bidderCtx(Id5IdModuleContext moduleContext) {
        final Timeout timeout = new TimeoutFactory(Clock.systemUTC()).create(1000);
        final AuctionInvocationContext auctionCtx = AuctionInvocationContextImpl.of(
                InvocationContextImpl.of(timeout, null, Endpoint.openrtb2_auction),
                AuctionContext.builder().account(Account.builder().id("acc").build()).build(),
                false,
                null,
                moduleContext);
        return BidderInvocationContextImpl.of(auctionCtx, "bidder");
    }
}
