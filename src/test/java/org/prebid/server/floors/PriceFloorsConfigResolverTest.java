package org.prebid.server.floors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;
import org.prebid.server.settings.model.AccountPriceFloorsFetchConfig;

import java.util.function.UnaryOperator;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class PriceFloorsConfigResolverTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Metrics metrics;

    private PriceFloorsConfigResolver target;

    @Before
    public void setUp() {
        target = new PriceFloorsConfigResolver(metrics);
    }

    @Test
    public void resolveShouldNotChangeAccountIfConfigIsValid() {
        // given
        final Account givenAccount = accountWithFloorsFetchConfig(identity());

        // when
        final Account actualAccount = target.resolve(givenAccount, defaultPriceConfig());

        // then
        assertThat(actualAccount).isSameAs(givenAccount);
        verifyNoInteractions(metrics);
    }

    @Test
    public void resolveShouldReturnGivenAccountIfEnforceFloorsRateLessThanMinimumValue() {
        // given
        final Account givenAccount = Account.builder()
                .id("some-id")
                .auction(AccountAuctionConfig.builder()
                        .priceFloors(AccountPriceFloorsConfig.builder()
                                .enforceFloorsRate(-1).build())
                        .build())
                .build();

        // when
        final Account actualAccount = target.resolve(givenAccount, defaultPriceConfig());

        // then
        assertThat(actualAccount).isEqualTo(fallbackAccount());
        verify(metrics).updateAlertsConfigFailed("some-id", MetricName.price_floors);
    }

    @Test
    public void resolveShouldReturnGivenAccountIfEnforceFloorsRateMoreThanMaximumValue() {
        // given
        final Account givenAccount = Account.builder()
                .id("some-id")
                .auction(AccountAuctionConfig.builder()
                        .priceFloors(AccountPriceFloorsConfig.builder()
                                .enforceFloorsRate(101).build())
                        .build())
                .build();

        // when
        final Account actualAccount = target.resolve(givenAccount, defaultPriceConfig());

        // then
        assertThat(actualAccount).isEqualTo(fallbackAccount());
        verify(metrics).updateAlertsConfigFailed("some-id", MetricName.price_floors);
    }

    @Test
    public void resolveShouldReturnGivenAccountIfPeriodicSecLessThanMinimumValue() {
        // given
        final Account givenAccount = accountWithFloorsFetchConfig(config -> config.periodSec(200L));

        // when
        final Account actualAccount = target.resolve(givenAccount, defaultPriceConfig());

        // then
        assertThat(actualAccount).isEqualTo(fallbackAccount());
        verify(metrics).updateAlertsConfigFailed("some-id", MetricName.price_floors);
    }

    @Test
    public void resolveShouldReturnGivenAccountIfPeriodicSecMoreThanMaxAgeSec() {
        // given
        final Account givenAccount = accountWithFloorsFetchConfig(config -> config.periodSec(900L).maxAgeSec(800L));

        // when
        final Account actualAccount = target.resolve(givenAccount, defaultPriceConfig());

        // then
        assertThat(actualAccount).isEqualTo(fallbackAccount());
        verify(metrics).updateAlertsConfigFailed("some-id", MetricName.price_floors);
    }

    @Test
    public void resolveShouldReturnGivenAccountIfMaxAgeSecLessThanMinimumValue() {
        // given
        final Account givenAccount = accountWithFloorsFetchConfig(config -> config.maxAgeSec(500L));

        // when
        final Account actualAccount = target.resolve(givenAccount, defaultPriceConfig());

        // then
        assertThat(actualAccount).isEqualTo(fallbackAccount());
        verify(metrics).updateAlertsConfigFailed("some-id", MetricName.price_floors);
    }

    @Test
    public void resolveShouldReturnGivenAccountIfMaxAgeSecMoreThanMaximumValue() {
        // given
        final Account givenAccount = accountWithFloorsFetchConfig(config -> config.maxAgeSec(Integer.MAX_VALUE + 1L));

        // when
        final Account actualAccount = target.resolve(givenAccount, defaultPriceConfig());

        // then
        assertThat(actualAccount).isEqualTo(fallbackAccount());
        verify(metrics).updateAlertsConfigFailed("some-id", MetricName.price_floors);
    }

    @Test
    public void resolveShouldReturnGivenAccountIfTimeoutLessThanMinimumValue() {
        // given
        final Account givenAccount = accountWithFloorsFetchConfig(config -> config.timeoutMs(9L));

        // when
        final Account actualAccount = target.resolve(givenAccount, defaultPriceConfig());

        // then
        assertThat(actualAccount).isEqualTo(fallbackAccount());
        verify(metrics).updateAlertsConfigFailed("some-id", MetricName.price_floors);
    }

    @Test
    public void resolveShouldReturnGivenAccountIfTimeoutMoreThanMaximumValue() {
        // given
        final Account givenAccount = accountWithFloorsFetchConfig(config -> config.timeoutMs(12000L));

        // when
        final Account actualAccount = target.resolve(givenAccount, defaultPriceConfig());

        // then
        assertThat(actualAccount).isEqualTo(fallbackAccount());
        verify(metrics).updateAlertsConfigFailed("some-id", MetricName.price_floors);
    }

    @Test
    public void resolveShouldReturnGivenAccountIfMaxRulesLessThanMinimumValue() {
        // given
        final Account givenAccount = accountWithFloorsFetchConfig(config -> config.maxRules(-1L));

        // when
        final Account actualAccount = target.resolve(givenAccount, defaultPriceConfig());

        // then
        assertThat(actualAccount).isEqualTo(fallbackAccount());
        verify(metrics).updateAlertsConfigFailed("some-id", MetricName.price_floors);
    }

    @Test
    public void resolveShouldReturnGivenAccountIfMaxRulesMoreThanMaximumValue() {
        // given
        final Account givenAccount = accountWithFloorsFetchConfig(config -> config.maxRules(Integer.MAX_VALUE + 1L));

        // when
        final Account actualAccount = target.resolve(givenAccount, defaultPriceConfig());

        // then
        assertThat(actualAccount).isEqualTo(fallbackAccount());
        verify(metrics).updateAlertsConfigFailed("some-id", MetricName.price_floors);
    }

    @Test
    public void resolveShouldReturnGivenAccountIfMaxFileSizeLessThanMinimumValue() {
        // given
        final Account givenAccount = accountWithFloorsFetchConfig(config -> config.maxFileSizeKb(-1L));

        // when
        final Account actualAccount = target.resolve(givenAccount, defaultPriceConfig());

        // then
        assertThat(actualAccount).isEqualTo(fallbackAccount());
        verify(metrics).updateAlertsConfigFailed("some-id", MetricName.price_floors);
    }

    @Test
    public void resolveShouldReturnGivenAccountIfMaxFileSizeMoreThanMaximumValue() {
        // given
        final Account givenAccount = accountWithFloorsFetchConfig(config -> config.maxFileSizeKb(Integer.MAX_VALUE + 1L));

        // when
        final Account actualAccount = target.resolve(givenAccount, defaultPriceConfig());

        // then
        assertThat(actualAccount).isEqualTo(fallbackAccount());
        verify(metrics).updateAlertsConfigFailed("some-id", MetricName.price_floors);
    }

    private static Account accountWithFloorsFetchConfig(
            UnaryOperator<AccountPriceFloorsFetchConfig.AccountPriceFloorsFetchConfigBuilder> configCustomizer) {
        return Account.builder()
                .id("some-id")
                .auction(AccountAuctionConfig.builder()
                        .priceFloors(AccountPriceFloorsConfig.builder()
                                .enforceFloorsRate(10)
                                .fetch(configCustomizer.apply(
                                        AccountPriceFloorsFetchConfig.builder()
                                                .maxAgeSec(1000L)
                                                .periodSec(600L)
                                                .timeoutMs(100L)
                                                .maxRules(100L)
                                                .maxFileSizeKb(100L))
                                        .build())
                                .build())
                        .build())
                .build();
    }

    private static Account fallbackAccount() {
        return Account.builder()
                .id("some-id")
                .auction(AccountAuctionConfig.builder().priceFloors(defaultPriceConfig()).build())
                .build();
    }

    private static AccountPriceFloorsConfig defaultPriceConfig() {
        return AccountPriceFloorsConfig.builder()
                .enabled(true)
                .enforceFloorsRate(3)
                .adjustForBidAdjustment(false)
                .build();
    }
}
