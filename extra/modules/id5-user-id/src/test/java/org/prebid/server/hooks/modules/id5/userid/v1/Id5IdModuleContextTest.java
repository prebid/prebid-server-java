package org.prebid.server.hooks.modules.id5.userid.v1;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.execution.timeout.TimeoutFactory;
import org.prebid.server.hooks.execution.v1.InvocationContextImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionInvocationContextImpl;
import org.prebid.server.hooks.modules.id5.userid.v1.model.Id5UserId;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.model.Endpoint;
import org.prebid.server.settings.model.Account;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class Id5IdModuleContextTest {

    @Test
    void shouldReturnProvidedModuleContextWhenPresent() {
        // given
        final Future<Id5UserId> future = Future.succeededFuture();
        final Id5IdModuleContext moduleContext = new Id5IdModuleContext(future);

        final Timeout timeout = new TimeoutFactory(Clock.systemUTC()).create(1000);
        final AuctionInvocationContext invocation = AuctionInvocationContextImpl.of(
                InvocationContextImpl.of(timeout, Endpoint.openrtb2_auction),
                AuctionContext.builder().account(Account.builder().id("acc").build()).build(),
                false,
                null,
                moduleContext);

        // when
        final Id5IdModuleContext result = Id5IdModuleContext.from(invocation);

        // then
        assertThat(result).isSameAs(moduleContext);
        assertThat(result.getId5UserIdFuture()).isSameAs(future);
    }

    @Test
    void shouldReturnEmptyModuleContextWhenAbsent() {
        // given
        final Timeout timeout = new TimeoutFactory(Clock.systemUTC()).create(1000);
        final AuctionInvocationContext invocation = AuctionInvocationContextImpl.of(
                InvocationContextImpl.of(timeout, Endpoint.openrtb2_auction),
                AuctionContext.builder().account(Account.builder().id("acc").build()).build(),
                false,
                null,
                null);

        // when
        final Id5IdModuleContext result = Id5IdModuleContext.from(invocation);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId5UserIdFuture()).isNotNull();
        assertThat(result.getId5UserIdFuture().succeeded()).isTrue();
        assertThat(result.getId5UserIdFuture().result()).isNull();
    }
}
