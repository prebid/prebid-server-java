package org.prebid.server.cookie;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.UsersyncMethod;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.cookie.model.CookieSyncContext;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountCookieSyncConfig;
import org.prebid.server.settings.model.AccountCoopSyncConfig;
import org.prebid.server.spring.config.bidder.model.usersync.CookieFamilySource;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

public class CoopSyncProviderTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderCatalog bidderCatalog;

    private CoopSyncProvider target;

    @Before
    public void setUp() {
        target = new CoopSyncProvider(bidderCatalog, Collections.emptySet(), false);
    }

    @Test
    public void creationShouldFilterInvalidPrioritizedBidders() {
        // given
        given(bidderCatalog.usersyncReadyBidders()).willReturn(Collections.emptySet());
        given(bidderCatalog.isValidName("invalid1")).willReturn(false);
        given(bidderCatalog.isValidName("invalid2")).willReturn(false);
        givenValidBidderWithCookieSync("valid");

        target = new CoopSyncProvider(bidderCatalog, Set.of("invalid1", "invalid2", "valid"), false);

        // when
        Set<String> result = target.coopSyncBidders(
                CookieSyncContext.builder()
                        .cookieSyncRequest(CookieSyncRequest.builder().coopSync(true).build())
                        .build());

        // then
        assertThat(result).containsExactly("valid");
    }

    @Test
    public void coopSyncBiddersShouldNotReturnBiddersIfRequestDisabledCoopSync() {
        // given
        givenCoopSyncProviderWithCoopSyncBidders("valid");

        // when
        Set<String> result = target.coopSyncBidders(
                CookieSyncContext.builder().cookieSyncRequest(CookieSyncRequest.builder().coopSync(false).build())
                        .build());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void coopSyncBiddersShouldReturnBiddersIfRequestEnabledCoopSync() {
        // given
        givenCoopSyncProviderWithCoopSyncBidders("valid");

        // when
        final Set<String> result = target.coopSyncBidders(
                CookieSyncContext.builder().cookieSyncRequest(CookieSyncRequest.builder().coopSync(true).build())
                        .build());

        // then
        assertThat(result).containsExactly("valid");
    }

    @Test
    public void coopSyncBiddersShouldNotReturnBiddersIfAccountDisabledCoopSyncRequestOmittedToggle() {
        // given
        givenCoopSyncProviderWithCoopSyncBidders("valid");

        final AccountCookieSyncConfig cookieSyncConfig =
                AccountCookieSyncConfig.of(1, 1, null, AccountCoopSyncConfig.of(false));

        // when
        final Set<String> result = target.coopSyncBidders(
                CookieSyncContext.builder().cookieSyncRequest(CookieSyncRequest.builder().build())
                        .account(Account.builder().cookieSync(cookieSyncConfig).build())
                        .build());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void coopSyncBiddersShouldReturnBiddersIfAccountEnabledCoopSyncAndRequestOmittedToggle() {
        // given
        givenCoopSyncProviderWithCoopSyncBidders("valid");

        final AccountCookieSyncConfig cookieSyncConfig =
                AccountCookieSyncConfig.of(1, 1, null, AccountCoopSyncConfig.of(true));

        // when
        final Set<String> result = target.coopSyncBidders(
                CookieSyncContext.builder().cookieSyncRequest(CookieSyncRequest.builder().build())
                        .account(Account.builder().cookieSync(cookieSyncConfig).build())
                        .build());

        // then
        assertThat(result).containsExactly("valid");
    }

    @Test
    public void coopSyncBiddersShouldNotReturnBiddersIfDefaultCoopSyncFalseAndRequestAndAccountOmittedToggle() {
        // given
        givenCoopSyncProviderWithCoopSyncBidders(false, "valid");

        // when
        final Set<String> result = target.coopSyncBidders(
                CookieSyncContext.builder().cookieSyncRequest(CookieSyncRequest.builder().build())
                        .account(Account.builder().build())
                        .build());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void coopSyncBiddersShouldReturnBiddersIfDefaultCoopSyncTrueAndRequestAndAccountOmittedToggle() {
        // given
        givenCoopSyncProviderWithCoopSyncBidders(true, "valid");

        // when
        final Set<String> result = target.coopSyncBidders(
                CookieSyncContext.builder().cookieSyncRequest(CookieSyncRequest.builder().build())
                        .account(Account.builder().build())
                        .build());

        // then
        assertThat(result).containsExactly("valid");
    }

    @Test
    public void coopSyncBiddersShouldReturnSetWithConfigPrioritizedBiddersFirstIfCoopSyncEnabled() {
        // given
        givenValidBidderWithCookieSync("bidder1");
        givenValidBidderWithCookieSync("bidder2");
        givenValidBidderWithCookieSync("bidder3");

        given(bidderCatalog.usersyncReadyBidders())
                .willReturn(Set.of("bidder1", "bidder2", "bidder3"));

        target = new CoopSyncProvider(bidderCatalog, singleton("bidder3"), false);

        // when
        final Set<String> result = target.coopSyncBidders(
                CookieSyncContext.builder()
                        .cookieSyncRequest(CookieSyncRequest.builder().coopSync(true).build())
                        .build());

        // then
        assertThat(result).first().isEqualTo("bidder3");
        assertThat(result).containsExactlyInAnyOrder("bidder1", "bidder2", "bidder3");
    }

    @Test
    public void coopSyncBiddersShouldReturnSetWithPrioritizedBiddersFromAccountFirstIfCoopSyncEnabled() {
        // given
        givenValidBidderWithCookieSync("bidder1");
        givenValidBidderWithCookieSync("bidder2");
        givenValidBidderWithCookieSync("bidder3");

        given(bidderCatalog.usersyncReadyBidders())
                .willReturn(Set.of("bidder1", "bidder2", "bidder3"));

        target = new CoopSyncProvider(bidderCatalog, singleton("bidder3"), false);

        final Account account = Account.builder()
                .cookieSync(
                        AccountCookieSyncConfig.of(
                                1,
                                1,
                                singleton("bidder2"),
                                AccountCoopSyncConfig.of(true)))
                .build();

        // when
        final Set<String> result = target.coopSyncBidders(
                CookieSyncContext.builder()
                        .cookieSyncRequest(CookieSyncRequest.builder().coopSync(true).build())
                        .account(account)
                        .build());

        // then
        assertThat(result).first().isEqualTo("bidder2");
        assertThat(result).containsExactlyInAnyOrder("bidder1", "bidder2", "bidder3");
    }

    private void givenCoopSyncProviderWithCoopSyncBidders(String... bidders) {
        givenCoopSyncProviderWithCoopSyncBidders(false, bidders);
    }

    private void givenCoopSyncProviderWithCoopSyncBidders(boolean defaultCoopSync, String... bidders) {
        Arrays.stream(bidders).forEach(this::givenValidBidderWithCookieSync);
        target = new CoopSyncProvider(
                bidderCatalog, Arrays.stream(bidders).collect(Collectors.toSet()), defaultCoopSync);
    }

    private void givenValidBidderWithCookieSync(String bidder) {
        given(bidderCatalog.isValidName(bidder)).willReturn(true);
        given(bidderCatalog.isActive(bidder)).willReturn(true);
        given(bidderCatalog.usersyncerByName(bidder)).willReturn(
                Optional.of(Usersyncer.of(
                        "cookie-family-name",
                        CookieFamilySource.ROOT,
                        UsersyncMethod.builder().build(),
                        null)));
    }
}
