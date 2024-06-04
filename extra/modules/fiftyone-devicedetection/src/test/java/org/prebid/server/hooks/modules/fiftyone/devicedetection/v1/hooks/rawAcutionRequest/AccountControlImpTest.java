package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.rawAcutionRequest;

import org.junit.Test;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.ModuleConfig;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceEnricher;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.FiftyOneDeviceDetectionRawAuctionRequestHook;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.AccountFilter;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.settings.model.Account;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

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

    private static Predicate<AuctionInvocationContext> buildHook(AccountFilter accountFilter) throws Exception {

        final ModuleConfig moduleConfig = new ModuleConfig();
        moduleConfig.setAccountFilter(accountFilter);
        return new FiftyOneDeviceDetectionRawAuctionRequestHook(
                moduleConfig,
                mock(DeviceEnricher.class)
        ) {
            @Override
            public boolean isAccountAllowed(AuctionInvocationContext invocationContext) {

                return super.isAccountAllowed(invocationContext);
            }

        }
            ::isAccountAllowed;
    }

    @Test
    public void shouldReturnTrueWhenFilterIsNull() throws Exception {

        // given
        final Predicate<AuctionInvocationContext> accountControl = buildHook(null);

        // when and then
        assertThat(accountControl.test(null)).isTrue();
    }

    @Test
    public void shouldReturnTrueWithoutWhitelistAndNoAuctionInvocationContext() throws Exception {

        // given
        final Predicate<AuctionInvocationContext> accountControl = buildHook(NO_WHITELIST_FILTER);

        // when and then
        assertThat(accountControl.test(null)).isTrue();
    }

    @Test
    public void shouldReturnTrueWithEmptyWhitelistAndNoAuctionInvocationContext() throws Exception {

        // given
        final Predicate<AuctionInvocationContext> accountControl = buildHook(EMPTY_WHITELIST_FILTER);

        // when and then
        assertThat(accountControl.test(null)).isTrue();
    }

    @Test
    public void shouldReturnFalseWithFilledWhitelistAndNoAuctionInvocationContext() throws Exception {

        // given
        final Predicate<AuctionInvocationContext> accountControl = buildHook(FILLED_WHITELIST_FILTER);

        // when and then
        assertThat(accountControl.test(null)).isFalse();
    }

    @Test
    public void shouldReturnTrueWithoutWhitelistAndNoAuctionContext() throws Exception {

        // given
        final Predicate<AuctionInvocationContext> accountControl = buildHook(NO_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        when(context.auctionContext()).thenReturn(null);

        // when and then
        assertThat(accountControl.test(context)).isTrue();
    }

    @Test
    public void shouldReturnTrueWithEmptyWhitelistAndNoAuctionContext() throws Exception {

        // given
        final Predicate<AuctionInvocationContext> accountControl = buildHook(EMPTY_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        when(context.auctionContext()).thenReturn(null);

        // when and then
        assertThat(accountControl.test(context)).isTrue();
    }

    @Test
    public void shouldReturnFalseWithFilledWhitelistAndNoAuctionContext() throws Exception {

        // given
        final Predicate<AuctionInvocationContext> accountControl = buildHook(FILLED_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        when(context.auctionContext()).thenReturn(null);

        // when and then
        assertThat(accountControl.test(context)).isFalse();
    }

    @Test
    public void shouldReturnTrueWithoutWhitelistAndNoAccount() throws Exception {

        // given
        final Predicate<AuctionInvocationContext> accountControl = buildHook(NO_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder().build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.test(context)).isTrue();
    }

    @Test
    public void shouldReturnTrueWithEmptyWhitelistAndNoAccount() throws Exception {

        // given
        final Predicate<AuctionInvocationContext> accountControl = buildHook(EMPTY_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder().build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.test(context)).isTrue();
    }

    @Test
    public void shouldReturnFalseWithFilledWhitelistAndNoAccount() throws Exception {

        // given
        final Predicate<AuctionInvocationContext> accountControl = buildHook(FILLED_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder().build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.test(context)).isFalse();
    }

    @Test
    public void shouldReturnTrueWithoutWhitelistAndNoAccountID() throws Exception {

        // given
        final Predicate<AuctionInvocationContext> accountControl = buildHook(NO_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .build())
                .build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.test(context)).isTrue();
    }

    @Test
    public void shouldReturnTrueWithEmptyWhitelistAndNoAccountID() throws Exception {

        // given
        final Predicate<AuctionInvocationContext> accountControl = buildHook(EMPTY_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .build())
                .build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.test(context)).isTrue();
    }

    @Test
    public void shouldReturnFalseWithFilledWhitelistAndNoAccountID() throws Exception {

        // given
        final Predicate<AuctionInvocationContext> accountControl = buildHook(FILLED_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .build())
                .build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.test(context)).isFalse();
    }

    @Test
    public void shouldReturnTrueWithoutWhitelistAndEmptyAccountID() throws Exception {

        // given
        final Predicate<AuctionInvocationContext> accountControl = buildHook(NO_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id("")
                        .build())
                .build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.test(context)).isTrue();
    }

    @Test
    public void shouldReturnTrueWithEmptyWhitelistAndEmptyAccountID() throws Exception {

        // given
        final Predicate<AuctionInvocationContext> accountControl = buildHook(EMPTY_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id("")
                        .build())
                .build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.test(context)).isTrue();
    }

    @Test
    public void shouldReturnFalseWithFilledWhitelistAndEmptyAccountID() throws Exception {

        // given
        final Predicate<AuctionInvocationContext> accountControl = buildHook(FILLED_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id("")
                        .build())
                .build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.test(context)).isFalse();
    }

    @Test
    public void shouldReturnTrueWithoutWhitelistAndAllowedAccountID() throws Exception {

        // given
        final Predicate<AuctionInvocationContext> accountControl = buildHook(NO_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id(ALLOWED_PUBLISHER_ID)
                        .build())
                .build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.test(context)).isTrue();
    }

    @Test
    public void shouldReturnTrueWithEmptyWhitelistAndAllowedAccountID() throws Exception {

        // given
        final Predicate<AuctionInvocationContext> accountControl = buildHook(EMPTY_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id(ALLOWED_PUBLISHER_ID)
                        .build())
                .build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.test(context)).isTrue();
    }

    @Test
    public void shouldReturnTrueWithFilledWhitelistAndAllowedAccountID() throws Exception {

        // given
        final Predicate<AuctionInvocationContext> accountControl = buildHook(FILLED_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id(ALLOWED_PUBLISHER_ID)
                        .build())
                .build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.test(context)).isTrue();
    }

    @Test
    public void shouldReturnTrueWithoutWhitelistAndNotAllowedAccountID() throws Exception {

        // given
        final Predicate<AuctionInvocationContext> accountControl = buildHook(NO_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id(NOT_ALLOWED_PUBLISHER_ID)
                        .build())
                .build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.test(context)).isTrue();
    }

    @Test
    public void shouldReturnTrueWithEmptyWhitelistAndNotAllowedAccountID() throws Exception {

        // given
        final Predicate<AuctionInvocationContext> accountControl = buildHook(EMPTY_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id(NOT_ALLOWED_PUBLISHER_ID)
                        .build())
                .build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.test(context)).isTrue();
    }

    @Test
    public void shouldReturnFalseWithFilledWhitelistAndNotAllowedAccountID() throws Exception {

        // given
        final Predicate<AuctionInvocationContext> accountControl = buildHook(FILLED_WHITELIST_FILTER);

        final AuctionInvocationContext context = mock(AuctionInvocationContext.class);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id(NOT_ALLOWED_PUBLISHER_ID)
                        .build())
                .build();
        when(context.auctionContext()).thenReturn(auctionContext);

        // when and then
        assertThat(accountControl.test(context)).isFalse();
    }
}
