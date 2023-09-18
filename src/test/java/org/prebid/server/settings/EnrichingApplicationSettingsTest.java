package org.prebid.server.settings;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.execution.Timeout;
import org.prebid.server.floors.PriceFloorsConfigResolver;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.EnabledForRequestType;
import org.prebid.server.settings.model.activity.AccountActivityConfiguration;
import org.prebid.server.settings.model.activity.rule.AccountActivityComponentRuleConfig;

import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.prebid.server.assertion.FutureAssertion.assertThat;

public class EnrichingApplicationSettingsTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ApplicationSettings delegate;
    @Mock
    private PriceFloorsConfigResolver priceFloorsConfigResolver;
    private final JsonMerger jsonMerger = new JsonMerger(jacksonMapper);

    private EnrichingApplicationSettings enrichingApplicationSettings;

    @Mock
    private Timeout timeout;

    @Before
    public void setUp() {
        given(priceFloorsConfigResolver.updateFloorsConfig(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));
    }

    @Test
    public void getAccountByIdShouldOmitMergingWhenDefaultAccountIsNull() {
        // given
        enrichingApplicationSettings = new EnrichingApplicationSettings(
                true, 0, null, delegate, priceFloorsConfigResolver, jsonMerger, jacksonMapper);

        final Account returnedAccount = Account.builder().build();
        given(delegate.getAccountById(anyString(), any())).willReturn(Future.succeededFuture(returnedAccount));

        // when
        final Future<Account> accountFuture = enrichingApplicationSettings.getAccountById("123", timeout);

        // then
        assertThat(accountFuture).isSucceeded();
        assertThat(accountFuture.result()).isSameAs(returnedAccount);

        verify(delegate).getAccountById(eq("123"), eq(timeout));
    }

    @Test
    public void getAccountByIdShouldOmitMergingWhenDefaultAccountIsEmpty() {
        // given
        enrichingApplicationSettings = new EnrichingApplicationSettings(
                true,
                0,
                "{}",
                delegate,
                priceFloorsConfigResolver,
                jsonMerger,
                jacksonMapper);

        final Account returnedAccount = Account.builder().build();
        given(delegate.getAccountById(anyString(), any())).willReturn(Future.succeededFuture(returnedAccount));

        // when
        final Future<Account> accountFuture = enrichingApplicationSettings.getAccountById("123", timeout);

        // then
        assertThat(accountFuture).isSucceeded();
        assertThat(accountFuture.result()).isSameAs(returnedAccount);

        verify(delegate).getAccountById(eq("123"), eq(timeout));
    }

    @Test
    public void getAccountByIdShouldMergeAccountWithDefaultAccount() {
        // given
        enrichingApplicationSettings = new EnrichingApplicationSettings(
                true,
                0,
                "{\"auction\": {\"banner-cache-ttl\": 100},"
                        + "\"privacy\": {\"gdpr\": {\"enabled\": true, \"channel-enabled\": {\"web\": false}}}}",
                delegate,
                priceFloorsConfigResolver,
                jsonMerger,
                jacksonMapper);

        given(delegate.getAccountById(anyString(), any())).willReturn(Future.succeededFuture(Account.builder()
                .id("123")
                .auction(AccountAuctionConfig.builder()
                        .videoCacheTtl(200)
                        .build())
                .privacy(AccountPrivacyConfig.of(
                        AccountGdprConfig.builder()
                                .enabledForRequestType(EnabledForRequestType.of(true, null, null, null))
                                .build(),
                        null,
                        null,
                        null))
                .build()));

        // when
        final Future<Account> accountFuture = enrichingApplicationSettings.getAccountById("123", timeout);

        // then
        assertThat(accountFuture).succeededWith(Account.builder()
                .id("123")
                .auction(AccountAuctionConfig.builder()
                        .bannerCacheTtl(100)
                        .videoCacheTtl(200)
                        .build())
                .privacy(AccountPrivacyConfig.of(
                        AccountGdprConfig.builder()
                                .enabled(true)
                                .enabledForRequestType(EnabledForRequestType.of(true, null, null, null))
                                .build(),
                        null,
                        null,
                        null))
                .build());
    }

    @Test
    public void getAccountByIdShouldReturnDefaultAccountWhenDelegateFailed() {
        // given
        enrichingApplicationSettings = new EnrichingApplicationSettings(
                false,
                0,
                "{\"auction\": {\"banner-cache-ttl\": 100}}",
                delegate,
                priceFloorsConfigResolver,
                jsonMerger,
                jacksonMapper);

        given(delegate.getAccountById(anyString(), any())).willReturn(Future.failedFuture("Exception"));

        // when
        final Future<Account> accountFuture = enrichingApplicationSettings.getAccountById("123", timeout);

        // then
        assertThat(accountFuture).succeededWith(Account.builder()
                .id("123")
                .auction(AccountAuctionConfig.builder()
                        .bannerCacheTtl(100)
                        .build())
                .build());
    }

    @Test
    public void getAccountByIdShouldReturnFailedFutureWhenDelegateFailedAndEnforceValidAccountIsTrue() {
        // given
        enrichingApplicationSettings = new EnrichingApplicationSettings(
                true,
                0,
                "{\"auction\": {\"banner-cache-ttl\": 100}}",
                delegate,
                priceFloorsConfigResolver,
                jsonMerger,
                jacksonMapper);

        given(delegate.getAccountById(anyString(), any())).willReturn(Future.failedFuture("Exception"));

        // when
        final Future<Account> accountFuture = enrichingApplicationSettings.getAccountById("123", timeout);

        // then
        assertThat(accountFuture).isFailed();
    }

    @Test
    public void getAccountByIdShouldPassOnFailureWhenDefaultAccountIsEmpty() {
        // given
        enrichingApplicationSettings = new EnrichingApplicationSettings(
                true,
                0,
                "{}",
                delegate,
                priceFloorsConfigResolver,
                jsonMerger,
                jacksonMapper);

        given(delegate.getAccountById(anyString(), any())).willReturn(Future.failedFuture("Exception"));

        // when
        final Future<Account> accountFuture = enrichingApplicationSettings.getAccountById("123", timeout);

        // then
        assertThat(accountFuture).isFailed();
    }

    @Test
    public void getAccountByIdShouldRemoveInvalidRulesFromAccountActivitiesConfiguration() {
        // given
        enrichingApplicationSettings = new EnrichingApplicationSettings(
                true,
                0,
                "{}",
                delegate,
                priceFloorsConfigResolver,
                jsonMerger,
                jacksonMapper);

        given(delegate.getAccountById(anyString(), any())).willReturn(Future.succeededFuture(Account.builder()
                .privacy(AccountPrivacyConfig.of(
                        null,
                        null,
                        Map.of(
                                Activity.SYNC_USER, AccountActivityConfiguration.of(null, null),
                                Activity.CALL_BIDDER, AccountActivityConfiguration.of(null, asList(
                                        AccountActivityComponentRuleConfig.of(null, null),
                                        AccountActivityComponentRuleConfig.of(
                                                AccountActivityComponentRuleConfig.Condition.of(null, null),
                                                null),
                                        AccountActivityComponentRuleConfig.of(
                                                AccountActivityComponentRuleConfig.Condition.of(
                                                        emptyList(),
                                                        emptyList()),
                                                null),
                                        AccountActivityComponentRuleConfig.of(
                                                AccountActivityComponentRuleConfig.Condition.of(
                                                        singletonList(ComponentType.BIDDER), singletonList("bidder")),
                                                null)))),
                        null))
                .build()));

        // when
        final Future<Account> accountFuture = enrichingApplicationSettings.getAccountById("123", timeout);

        // then
        assertThat(accountFuture).succeededWith(Account.builder()
                .privacy(AccountPrivacyConfig.of(
                        null,
                        null,
                        Map.of(
                                Activity.SYNC_USER, AccountActivityConfiguration.of(null, null),
                                Activity.CALL_BIDDER, AccountActivityConfiguration.of(null, asList(
                                        AccountActivityComponentRuleConfig.of(null, null),
                                        AccountActivityComponentRuleConfig.of(
                                                AccountActivityComponentRuleConfig.Condition.of(null, null),
                                                null),
                                        AccountActivityComponentRuleConfig.of(
                                                AccountActivityComponentRuleConfig.Condition.of(
                                                        singletonList(ComponentType.BIDDER), singletonList("bidder")),
                                                null)))),
                        null))
                .build());
    }
}
