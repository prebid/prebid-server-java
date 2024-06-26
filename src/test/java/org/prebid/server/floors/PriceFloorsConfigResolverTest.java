package org.prebid.server.floors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

@ExtendWith(MockitoExtension.class)
public class PriceFloorsConfigResolverTest extends VertxTest {

    @Mock
    private Metrics metrics;

    private PriceFloorsConfigResolver target;

    @BeforeEach
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
        final Account givenAccount = accountWithFloorsFetchConfig(config -> config.timeout(9L));

        // when
        final Account actualAccount = target.resolve(givenAccount, defaultPriceConfig());

        // then
        assertThat(actualAccount).isEqualTo(fallbackAccount());
        verify(metrics).updateAlertsConfigFailed("some-id", MetricName.price_floors);
    }

    @Test
    public void resolveShouldReturnGivenAccountIfTimeoutMoreThanMaximumValue() {
        // given
        final Account givenAccount = accountWithFloorsFetchConfig(config -> config.timeout(12000L));

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
        final Account givenAccount = accountWithFloorsFetchConfig(config -> config.maxFileSize(-1L));

        // when
        final Account actualAccount = target.resolve(givenAccount, defaultPriceConfig());

        // then
        assertThat(actualAccount).isEqualTo(fallbackAccount());
        verify(metrics).updateAlertsConfigFailed("some-id", MetricName.price_floors);
    }

    @Test
    public void resolveShouldReturnGivenAccountIfMaxFileSizeMoreThanMaximumValue() {
        // given
        final Account givenAccount = accountWithFloorsFetchConfig(config -> config.maxFileSize(Integer.MAX_VALUE + 1L));

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
                                                .timeout(100L)
                                                .maxRules(100L)
                                                .maxFileSize(100L))
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
