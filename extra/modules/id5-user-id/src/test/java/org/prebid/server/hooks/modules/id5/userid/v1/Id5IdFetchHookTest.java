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
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.id5.userid.v1.fetch.HttpFetchClient;
import org.prebid.server.hooks.modules.id5.userid.v1.filter.FetchFilter;
import org.prebid.server.hooks.modules.id5.userid.v1.filter.FilterResult;
import org.prebid.server.hooks.modules.id5.userid.v1.model.Id5UserId;
import org.prebid.server.hooks.modules.id5.userid.v1.model.Id5PartnerIdProvider;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.model.Endpoint;
import org.prebid.server.settings.model.Account;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

public class Id5IdFetchHookTest {

    @Test
    public void shouldReturnNoInvocationAndSetModuleContextWithFutureWhenSampled() {
        // given
        final HttpFetchClient fetchClient = Mockito.mock(HttpFetchClient.class);
        final FetchFilter filter = Mockito.mock(FetchFilter.class);
        final Id5PartnerIdProvider partnerIdProvider = Mockito.mock(Id5PartnerIdProvider.class);
        final Future<Id5UserId> future = Future.succeededFuture(Id5UserId.empty());
        when(fetchClient.fetch(anyLong(), any(AuctionRequestPayload.class), any())).thenReturn(future);
        when(filter.shouldInvoke(any(AuctionRequestPayload.class), any())).thenReturn(FilterResult.accepted());
        when(partnerIdProvider.getPartnerId(any())).thenReturn(Optional.of(123L));

        final Id5IdFetchHook hook = new Id5IdFetchHook(fetchClient, List.of(filter), partnerIdProvider);

        final BidRequest bidRequest = BidRequest.builder().id("req-1").build();
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(bidRequest);

        final AuctionInvocationContext invocation = createAuctionContext();

        // when
        final Future<InvocationResult<AuctionRequestPayload>> resultFuture = hook.call(payload, invocation);
        final InvocationResult<AuctionRequestPayload> result = resultFuture.result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
        assertThat(result.moduleContext()).isInstanceOf(Id5IdModuleContext.class);
        final Id5IdModuleContext ctx = (Id5IdModuleContext) result.moduleContext();
        assertThat(ctx.getId5UserIdFuture()).isSameAs(future);
    }

    @Test
    public void shouldReturnNoInvocationWhenId5IdAlreadyPresent() {
        // given
        final HttpFetchClient fetchClient = Mockito.mock(HttpFetchClient.class);
        final Id5PartnerIdProvider partnerIdProvider = Mockito.mock(Id5PartnerIdProvider.class);
        final Id5IdFetchHook hook = new Id5IdFetchHook(fetchClient, List.of(), partnerIdProvider);

        final User userWithId5 = User.builder()
                .eids(List.of(Eid.builder()
                        .source("id5-sync.com")
                        .uids(List.of(Uid.builder().id("id5-xyz").build()))
                        .build()))
                .build();
        final BidRequest bidRequest = BidRequest.builder().user(userWithId5).build();
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(bidRequest);

        final AuctionInvocationContext invocation = createAuctionContext();

        // when
        final InvocationResult<AuctionRequestPayload> result = hook.call(payload, invocation).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_invocation);
        assertThat(result.moduleContext()).isNull();
    }

    @Test
    public void shouldReturnNoInvocationWhenSamplerRejects() {
        // given
        final HttpFetchClient fetchClient = Mockito.mock(HttpFetchClient.class);
        final FetchFilter filter = Mockito.mock(FetchFilter.class);
        final Id5PartnerIdProvider partnerIdProvider = Mockito.mock(Id5PartnerIdProvider.class);
        when(filter.shouldInvoke(any(AuctionRequestPayload.class), any()))
                .thenReturn(FilterResult.rejected("rejected by sampling"));

        final Id5IdFetchHook hook = new Id5IdFetchHook(fetchClient, List.of(filter), partnerIdProvider);

        final BidRequest bidRequest = BidRequest.builder().id("req-2").build();
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(bidRequest);

        final AuctionInvocationContext invocation = createAuctionContext();

        // when
        final InvocationResult<AuctionRequestPayload> result = hook.call(payload, invocation).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_invocation);
        assertThat(result.moduleContext()).isNull();
    }

    @Test
    public void shouldReturnNoInvocationWhenAnyFetchFilterRejectsMultipleFilters() {
        // given
        final HttpFetchClient fetchClient = Mockito.mock(HttpFetchClient.class);
        final Id5PartnerIdProvider partnerIdProvider = Mockito.mock(Id5PartnerIdProvider.class);
        final FetchFilter accept1 = Mockito.mock(FetchFilter.class);
        final FetchFilter reject = Mockito.mock(FetchFilter.class);
        final FetchFilter accept2 = Mockito.mock(FetchFilter.class);

        when(accept1.shouldInvoke(any(AuctionRequestPayload.class), any()))
                .thenReturn(FilterResult.accepted());
        when(reject.shouldInvoke(any(AuctionRequestPayload.class), any()))
                .thenReturn(FilterResult.rejected("block-by-second"));
        when(accept2.shouldInvoke(any(AuctionRequestPayload.class), any()))
                .thenReturn(FilterResult.accepted());

        final Id5IdFetchHook hook = new Id5IdFetchHook(
                fetchClient, List.of(accept1, reject, accept2), partnerIdProvider);

        final BidRequest bidRequest = BidRequest.builder().id("req-3").build();
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(bidRequest);

        final AuctionInvocationContext invocation = createAuctionContext();

        // when
        final InvocationResult<AuctionRequestPayload> result = hook.call(payload, invocation).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_invocation);
        assertThat(result.moduleContext()).isNull();
        // ensure fetch client was not called after filter rejection
        Mockito.verifyNoInteractions(fetchClient);
        // reason from rejecting filter should be present in debug messages
        assertThat(result.debugMessages()).anyMatch(m -> m.contains("block-by-second"));
    }

    @Test
    public void shouldReturnNoInvocationWhenPartnerIdNotConfigured() {
        // given
        final HttpFetchClient fetchClient = Mockito.mock(HttpFetchClient.class);
        final FetchFilter filter = Mockito.mock(FetchFilter.class);
        final Id5PartnerIdProvider partnerIdProvider = Mockito.mock(Id5PartnerIdProvider.class);
        when(filter.shouldInvoke(any(AuctionRequestPayload.class), any())).thenReturn(FilterResult.accepted());
        when(partnerIdProvider.getPartnerId(any())).thenReturn(Optional.empty());

        final Id5IdFetchHook hook = new Id5IdFetchHook(fetchClient, List.of(filter), partnerIdProvider);

        final BidRequest bidRequest = BidRequest.builder().id("req-5").build();
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(bidRequest);

        final AuctionInvocationContext invocation = createAuctionContext();

        // when
        final InvocationResult<AuctionRequestPayload> result = hook.call(payload, invocation).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_invocation);
        assertThat(result.moduleContext()).isNull();
        assertThat(result.debugMessages()).anyMatch(m -> m.contains("partner id not configured"));
        Mockito.verifyNoInteractions(fetchClient);
    }

    private static AuctionInvocationContextImpl createAuctionContext() {
        final Timeout timeout = new TimeoutFactory(Clock.systemUTC()).create(1000);
        return AuctionInvocationContextImpl.of(
                InvocationContextImpl.of(timeout, null, Endpoint.openrtb2_auction),
                AuctionContext.builder().account(Account.builder().id("acc").build()).build(),
                false,
                null,
                null
        );
    }
}
