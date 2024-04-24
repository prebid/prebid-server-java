package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps;

import org.junit.Test;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.AccountControl;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.config.AccountFilter;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.settings.model.Account;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AccountControlImpTest {
    private static final String ALLOWED_PUBLISHER_ID = "42";
    private static final String NOT_ALLOWED_PUBLISHER_ID = "29";

    private static final AccountFilter NO_WHITELIST_FILTER = new AccountFilter();
    private static final AccountFilter EMPTY_WHITELIST_FILTER = new AccountFilter();
    private static final AccountFilter FILLED_WHITELIST_FILTER = new AccountFilter();

    static {
        EMPTY_WHITELIST_FILTER.setAllowList(Collections.emptyList());
        FILLED_WHITELIST_FILTER.setAllowList(List.of(ALLOWED_PUBLISHER_ID));
    }

    @Test
    public void shouldReturnTrueWhenFilterIsNull() {
        // given
        final AccountControl accountControl = new AccountControlImp(null);

        // when and then
        assertThat(accountControl.isAllowed(null)).isTrue();
    }

    @Test
    public void shouldReturnTrueWithoutWhitelistAndNoAuctionInvocationContext() {
        // given
        final AccountControl accountControl = new AccountControlImp(NO_WHITELIST_FILTER);

        // when and then
        assertThat(accountControl.isAllowed(null)).isTrue();
    }

    @Test
    public void shouldReturnTrueWithEmptyWhitelistAndNoAuctionInvocationContext() {
        // given
        final AccountControl accountControl = new AccountControlImp(EMPTY_WHITELIST_FILTER);

        // when and then
        assertThat(accountControl.isAllowed(null)).isTrue();
    }

    @Test
    public void shouldReturnFalseWithFilledWhitelistAndNoAuctionInvocationContext() {
        // given
        final AccountControl accountControl = new AccountControlImp(FILLED_WHITELIST_FILTER);

        // when and then
        assertThat(accountControl.isAllowed(null)).isFalse();
    }

    @Test
    public void shouldReturnTrueWithoutWhitelistAndNoAuctionContext() {
        // given
        final AccountControl accountControl = new AccountControlImp(NO_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        when(context.auctionContext()).thenReturn(null);

        // when and then
        assertThat(accountControl.isAllowed(context)).isTrue();
    }

    @Test
    public void shouldReturnTrueWithEmptyWhitelistAndNoAuctionContext() {
        // given
        final AccountControl accountControl = new AccountControlImp(EMPTY_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        when(context.auctionContext()).thenReturn(null);

        // when and then
        assertThat(accountControl.isAllowed(context)).isTrue();
    }

    @Test
    public void shouldReturnFalseWithFilledWhitelistAndNoAuctionContext() {
        // given
        final AccountControl accountControl = new AccountControlImp(FILLED_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        when(context.auctionContext()).thenReturn(null);

        // when and then
        assertThat(accountControl.isAllowed(context)).isFalse();
    }

    @Test
    public void shouldReturnTrueWithoutWhitelistAndNoAccount() {
        // given
        final AccountControl accountControl = new AccountControlImp(NO_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder().build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.isAllowed(context)).isTrue();
    }

    @Test
    public void shouldReturnTrueWithEmptyWhitelistAndNoAccount() {
        // given
        final AccountControl accountControl = new AccountControlImp(EMPTY_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder().build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.isAllowed(context)).isTrue();
    }

    @Test
    public void shouldReturnFalseWithFilledWhitelistAndNoAccount() {
        // given
        final AccountControl accountControl = new AccountControlImp(FILLED_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder().build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.isAllowed(context)).isFalse();
    }

    @Test
    public void shouldReturnTrueWithoutWhitelistAndNoAccountID() {
        // given
        final AccountControl accountControl = new AccountControlImp(NO_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .build())
                .build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.isAllowed(context)).isTrue();
    }

    @Test
    public void shouldReturnTrueWithEmptyWhitelistAndNoAccountID() {
        // given
        final AccountControl accountControl = new AccountControlImp(EMPTY_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .build())
                .build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.isAllowed(context)).isTrue();
    }

    @Test
    public void shouldReturnFalseWithFilledWhitelistAndNoAccountID() {
        // given
        final AccountControl accountControl = new AccountControlImp(FILLED_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .build())
                .build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.isAllowed(context)).isFalse();
    }

    @Test
    public void shouldReturnTrueWithoutWhitelistAndEmptyAccountID() {
        // given
        final AccountControl accountControl = new AccountControlImp(NO_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id("")
                        .build())
                .build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.isAllowed(context)).isTrue();
    }

    @Test
    public void shouldReturnTrueWithEmptyWhitelistAndEmptyAccountID() {
        // given
        final AccountControl accountControl = new AccountControlImp(EMPTY_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id("")
                        .build())
                .build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.isAllowed(context)).isTrue();
    }

    @Test
    public void shouldReturnFalseWithFilledWhitelistAndEmptyAccountID() {
        // given
        final AccountControl accountControl = new AccountControlImp(FILLED_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id("")
                        .build())
                .build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.isAllowed(context)).isFalse();
    }

    @Test
    public void shouldReturnTrueWithoutWhitelistAndAllowedAccountID() {
        // given
        final AccountControl accountControl = new AccountControlImp(NO_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id(ALLOWED_PUBLISHER_ID)
                        .build())
                .build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.isAllowed(context)).isTrue();
    }

    @Test
    public void shouldReturnTrueWithEmptyWhitelistAndAllowedAccountID() {
        // given
        final AccountControl accountControl = new AccountControlImp(EMPTY_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id(ALLOWED_PUBLISHER_ID)
                        .build())
                .build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.isAllowed(context)).isTrue();
    }

    @Test
    public void shouldReturnTrueWithFilledWhitelistAndAllowedAccountID() {
        // given
        final AccountControl accountControl = new AccountControlImp(FILLED_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id(ALLOWED_PUBLISHER_ID)
                        .build())
                .build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.isAllowed(context)).isTrue();
    }

    @Test
    public void shouldReturnTrueWithoutWhitelistAndNotAllowedAccountID() {
        // given
        final AccountControl accountControl = new AccountControlImp(NO_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id(NOT_ALLOWED_PUBLISHER_ID)
                        .build())
                .build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.isAllowed(context)).isTrue();
    }

    @Test
    public void shouldReturnTrueWithEmptyWhitelistAndNotAllowedAccountID() {
        // given
        final AccountControl accountControl = new AccountControlImp(EMPTY_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id(NOT_ALLOWED_PUBLISHER_ID)
                        .build())
                .build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.isAllowed(context)).isTrue();
    }

    @Test
    public void shouldReturnFalseWithFilledWhitelistAndNotAllowedAccountID() {
        // given
        final AccountControl accountControl = new AccountControlImp(FILLED_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id(NOT_ALLOWED_PUBLISHER_ID)
                        .build())
                .build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.isAllowed(context)).isFalse();
    }
}
