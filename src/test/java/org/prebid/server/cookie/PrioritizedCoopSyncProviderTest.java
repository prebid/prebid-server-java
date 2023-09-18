package org.prebid.server.cookie;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.UsersyncMethod;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountCookieSyncConfig;
import org.prebid.server.settings.model.AccountCoopSyncConfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

public class PrioritizedCoopSyncProviderTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderCatalog bidderCatalog;

    private PrioritizedCoopSyncProvider target;

    @Test
    public void creationShouldFilterInvalidPrioritizedBidders() {
        // given
        given(bidderCatalog.usersyncReadyBidders()).willReturn(Collections.emptySet());
        given(bidderCatalog.isValidName("invalid1")).willReturn(false);
        given(bidderCatalog.isValidName("invalid2")).willReturn(false);
        givenValidBidderWithCookieSync("valid");

        target = new PrioritizedCoopSyncProvider(Set.of("invalid1", "invalid2", "valid"), bidderCatalog);

        // when
        final Set<String> result = target.prioritizedBidders(Account.empty("1001"));

        // then
        assertThat(result).containsExactly("valid");
    }

    @Test
    public void prioritizedBiddersShouldReturnSetWithPrioritizedBiddersFromAccount() {
        // given
        givenValidBiddersWithCookieSync("bidder1", "bidder2", "bidder3");

        target = new PrioritizedCoopSyncProvider(Set.of("bidder1", "bidder2", "bidder3"), bidderCatalog);

        final Account account = Account.builder()
                .cookieSync(
                        AccountCookieSyncConfig.of(
                                1,
                                1,
                                singleton("bidder2"),
                                AccountCoopSyncConfig.of(true)))
                .build();

        // when
        final Set<String> result = target.prioritizedBidders(account);

        // then
        assertThat(result).containsExactly("bidder2");
    }

    @Test
    public void isPrioritizedFamilyShouldReturnTrueIfCookieFamilyCorrespondsToPrioritizedBidder() {
        // given
        givenValidBidderWithCookieSync("bidder");

        target = new PrioritizedCoopSyncProvider(Set.of("bidder"), bidderCatalog);

        // when and then
        assertThat(target.isPrioritizedFamily("bidder-cookie-family")).isTrue();
    }

    @Test
    public void isPrioritizedFamilyShouldReturnFalseIfCookieFamilyDoesNotCorrespondToPrioritizedBidder() {
        // given
        givenValidBidderWithCookieSync("bidder");

        target = new PrioritizedCoopSyncProvider(Set.of("bidder"), bidderCatalog);

        // when and then
        assertThat(target.isPrioritizedFamily("invalid-cookie-family")).isFalse();
    }

    @Test
    public void hasPrioritizedBiddersShouldReturnTrueWhenThereArePrioritizedBiddersDefined() {
        // given
        givenValidBidderWithCookieSync("bidder");

        target = new PrioritizedCoopSyncProvider(Set.of("bidder"), bidderCatalog);

        // when and then
        assertThat(target.hasPrioritizedBidders()).isTrue();
    }

    @Test
    public void hasPrioritizedBiddersShouldReturnFalseWhenThereAreNoPrioritizedBiddersDefined() {
        // given
        target = new PrioritizedCoopSyncProvider(emptySet(), bidderCatalog);

        // when and then
        assertThat(target.hasPrioritizedBidders()).isFalse();
    }

    private void givenValidBiddersWithCookieSync(String... bidders) {
        Arrays.stream(bidders).forEach(this::givenValidBidderWithCookieSync);
        given(bidderCatalog.usersyncReadyBidders())
                .willReturn(Arrays.stream(bidders).collect(Collectors.toSet()));
    }

    private void givenValidBidderWithCookieSync(String bidder) {
        given(bidderCatalog.isValidName(bidder)).willReturn(true);
        given(bidderCatalog.isActive(bidder)).willReturn(true);
        given(bidderCatalog.cookieFamilyName(bidder)).willReturn(Optional.of(bidder + "-cookie-family"));
        given(bidderCatalog.usersyncerByName(bidder)).willReturn(
                Optional.of(Usersyncer.of(
                        "cookie-family-name",
                        UsersyncMethod.builder().build(),
                        null)));
    }
}
