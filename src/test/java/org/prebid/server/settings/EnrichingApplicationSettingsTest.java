package org.prebid.server.settings;

import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.activity.ActivitiesConfigResolver;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.floors.PriceFloorsConfigResolver;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.EnabledForRequestType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.prebid.server.assertion.FutureAssertion.assertThat;

@ExtendWith(MockitoExtension.class)
public class EnrichingApplicationSettingsTest extends VertxTest {

    @Mock
    private ApplicationSettings delegate;
    @Mock(strictness = LENIENT)
    private PriceFloorsConfigResolver priceFloorsConfigResolver;
    @Mock(strictness = LENIENT)
    private ActivitiesConfigResolver activitiesConfigResolver;

    private final JsonMerger jsonMerger = new JsonMerger(jacksonMapper);

    private EnrichingApplicationSettings target;

    @Mock
    private Timeout timeout;

    @BeforeEach
    public void setUp() {
        given(priceFloorsConfigResolver.resolve(any(), any())).willAnswer(invocation -> invocation.getArgument(0));
        given(activitiesConfigResolver.resolve(any())).willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void getAccountByIdShouldSuccessfullyMergeWhenDefaultAccountIsNull() {
        // given
        target = new EnrichingApplicationSettings(
                true,
                null,
                delegate,
                priceFloorsConfigResolver,
                activitiesConfigResolver,
                jsonMerger,
                jacksonMapper);

        final Account returnedAccount = Account.builder().build();
        given(delegate.getAccountById(anyString(), any())).willReturn(Future.succeededFuture(returnedAccount));

        // when
        final Future<Account> accountFuture = target.getAccountById("123", timeout);

        // then
        assertThat(accountFuture).succeededWith(returnedAccount);

        verify(delegate).getAccountById(eq("123"), eq(timeout));
    }

    @Test
    public void getAccountByIdShouldSuccessfullyMergeWhenDefaultAccountIsEmpty() {
        // given
        target = new EnrichingApplicationSettings(
                true,
                "{}",
                delegate,
                priceFloorsConfigResolver,
                activitiesConfigResolver, jsonMerger,
                jacksonMapper);

        final Account returnedAccount = Account.builder().build();
        given(delegate.getAccountById(anyString(), any())).willReturn(Future.succeededFuture(returnedAccount));

        // when
        final Future<Account> accountFuture = target.getAccountById("123", timeout);

        // then
        assertThat(accountFuture).succeededWith(returnedAccount);

        verify(delegate).getAccountById(eq("123"), eq(timeout));
    }

    @Test
    public void getAccountByIdShouldMergeAccountWithDefaultAccount() {
        // given
        target = new EnrichingApplicationSettings(
                true,
                "{\"auction\": {\"banner-cache-ttl\": 100, "
                        + "\"price-floors\": {\"enabled\": true, \"enforce-floors-rate\": 3}},"
                        + "\"privacy\": {\"gdpr\": {\"enabled\": true, \"channel-enabled\": {\"web\": false}}}}",
                delegate,
                priceFloorsConfigResolver,
                activitiesConfigResolver,
                jsonMerger,
                jacksonMapper);

        given(delegate.getAccountById(eq("123"), any())).willReturn(Future.succeededFuture(Account.builder()
                .id("123")
                .auction(AccountAuctionConfig.builder()
                        .videoCacheTtl(200)
                        .build())
                .privacy(AccountPrivacyConfig.builder()
                        .gdpr(AccountGdprConfig.builder()
                                .enabledForRequestType(EnabledForRequestType.of(true, null, null, null, null))
                                .build())
                        .build())
                .build()));

        // when
        final Future<Account> accountFuture = target.getAccountById("123", timeout);

        // then
        final Account expectedAccount = Account.builder()
                .id("123")
                .auction(AccountAuctionConfig.builder()
                        .bannerCacheTtl(100)
                        .videoCacheTtl(200)
                        .priceFloors(AccountPriceFloorsConfig.builder().enabled(true).enforceFloorsRate(3).build())
                        .build())
                .privacy(AccountPrivacyConfig.builder()
                        .gdpr(AccountGdprConfig.builder()
                                .enabled(true)
                                .enabledForRequestType(EnabledForRequestType.of(true, null, null, null, null))
                                .build())
                        .build())
                .build();
        assertThat(accountFuture).succeededWith(expectedAccount);

        verify(activitiesConfigResolver).resolve(eq(expectedAccount));
        verify(priceFloorsConfigResolver).resolve(
                eq(expectedAccount),
                eq(AccountPriceFloorsConfig.builder().enabled(true).enforceFloorsRate(3).build()));
    }

    @Test
    public void getAccountByIdShouldReturnDefaultAccountWhenDelegateFailed() {
        // given
        target = new EnrichingApplicationSettings(
                false,
                "{\"auction\": {\"banner-cache-ttl\": 100}}",
                delegate,
                priceFloorsConfigResolver,
                activitiesConfigResolver,
                jsonMerger,
                jacksonMapper);

        given(delegate.getAccountById(anyString(), any())).willReturn(Future.failedFuture("Exception"));

        // when
        final Future<Account> accountFuture = target.getAccountById("123", timeout);

        // then
        assertThat(accountFuture).succeededWith(Account.builder()
                .id("123")
                .auction(AccountAuctionConfig.builder()
                        .bannerCacheTtl(100)
                        .build())
                .build());
        verifyNoInteractions(priceFloorsConfigResolver, activitiesConfigResolver);
    }

    @Test
    public void getAccountByIdShouldReturnFailedFutureWhenAccountIdIsBlankAndEnforceValidAccountIsTrue() {
        // given
        target = new EnrichingApplicationSettings(
                true,
                "{\"auction\": {\"banner-cache-ttl\": 100}}",
                delegate,
                priceFloorsConfigResolver,
                activitiesConfigResolver,
                jsonMerger,
                jacksonMapper);

        // when
        final Future<Account> accountFuture = target.getAccountById("", timeout);

        // then
        assertThat(accountFuture).isFailed();
        verifyNoMoreInteractions(delegate);
    }

    @Test
    public void getAccountByIdShouldReturnEmptyAccWhenAccountIdIsBlankAndEnforceValidAccountIsFalse() {
        // given
        target = new EnrichingApplicationSettings(
                false,
                "{\"auction\": {\"banner-cache-ttl\": 100}}",
                delegate,
                priceFloorsConfigResolver,
                activitiesConfigResolver,
                jsonMerger,
                jacksonMapper);

        // when
        final Future<Account> accountFuture = target.getAccountById("", timeout);

        // then
        assertThat(accountFuture).succeededWith(Account.builder()
                .id("")
                .auction(AccountAuctionConfig.builder()
                        .bannerCacheTtl(100)
                        .build())
                .build());
        verifyNoMoreInteractions(delegate);
        verifyNoInteractions(priceFloorsConfigResolver, activitiesConfigResolver);
    }

    @Test
    public void getAccountByIdShouldPassOnFailureWhenDefaultAccountIsEmpty() {
        // given
        target = new EnrichingApplicationSettings(
                true,
                "{}",
                delegate,
                priceFloorsConfigResolver,
                activitiesConfigResolver,
                jsonMerger,
                jacksonMapper);

        given(delegate.getAccountById(anyString(), any())).willReturn(Future.failedFuture("Exception"));

        // when
        final Future<Account> accountFuture = target.getAccountById("123", timeout);

        // then
        assertThat(accountFuture).isFailed();
        verifyNoInteractions(priceFloorsConfigResolver, activitiesConfigResolver);
    }
}
