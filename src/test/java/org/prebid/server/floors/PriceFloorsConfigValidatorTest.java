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

public class PriceFloorsConfigValidatorTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Metrics metrics;

    private PriceFloorsConfigValidator target;

    @Before
    public void setUp() {
        target = new PriceFloorsConfigValidator(metrics);
    }

    @Test
    public void validateShouldNotChangeAccountIfConfigIsValid() {
        // given
        final Account givenAccount = accountWithFloorsFetchConfig(identity());

        // when
        final Account actualAccount = target.validate(givenAccount);

        // then
        assertThat(actualAccount).isSameAs(givenAccount);
        verifyNoInteractions(metrics);
    }

    @Test
    public void validateShouldReturnGivenAccountIfEnforceFloorsRateLessThanMinimumValue() {
        // given
        final Account givenAccount = Account.builder()
                .id("some-id")
                .auction(AccountAuctionConfig.builder()
                        .priceFloors(AccountPriceFloorsConfig.builder()
                                .enforceFloorsRate(-1).build())
                        .build())
                .build();

        // when
        final Account actualAccount = target.validate(givenAccount);

        // then
        assertThat(actualAccount).isEqualTo(fallbackAccount());
        verify(metrics).updateAlertsConfigFailed("some-id", MetricName.price_floors);
    }

    @Test
    public void validateShouldReturnGivenAccountIfEnforceFloorsRateMoreThanMaximumValue() {
        // given
        final Account givenAccount = Account.builder()
                .id("some-id")
                .auction(AccountAuctionConfig.builder()
                        .priceFloors(AccountPriceFloorsConfig.builder()
                                .enforceFloorsRate(101).build())
                        .build())
                .build();

        // when
        final Account actualAccount = target.validate(givenAccount);

        // then
        assertThat(actualAccount).isEqualTo(fallbackAccount());
        verify(metrics).updateAlertsConfigFailed("some-id", MetricName.price_floors);
    }

    @Test
    public void validateShouldReturnGivenAccountIfPeriodicSecLessThanMinimumValue() {
        // given
        final Account givenAccount = accountWithFloorsFetchConfig(config -> config.periodSec(200L));

        // when
        final Account actualAccount = target.validate(givenAccount);

        // then
        assertThat(actualAccount).isEqualTo(fallbackAccount());
        verify(metrics).updateAlertsConfigFailed("some-id", MetricName.price_floors);
    }

    @Test
    public void validateShouldReturnGivenAccountIfPeriodicSecMoreThanMaxAgeSec() {
        // given
        final Account givenAccount = accountWithFloorsFetchConfig(config -> config.periodSec(900L).maxAgeSec(800L));

        // when
        final Account actualAccount = target.validate(givenAccount);

        // then
        assertThat(actualAccount).isEqualTo(fallbackAccount());
        verify(metrics).updateAlertsConfigFailed("some-id", MetricName.price_floors);
    }

    @Test
    public void validateShouldReturnGivenAccountIfMaxAgeSecLessThanMinimumValue() {
        // given
        final Account givenAccount = accountWithFloorsFetchConfig(config -> config.maxAgeSec(500L));

        // when
        final Account actualAccount = target.validate(givenAccount);

        // then
        assertThat(actualAccount).isEqualTo(fallbackAccount());
        verify(metrics).updateAlertsConfigFailed("some-id", MetricName.price_floors);
    }

    @Test
    public void validateShouldReturnGivenAccountIfMaxAgeSecMoreThanMaximumValue() {
        // given
        final Account givenAccount = accountWithFloorsFetchConfig(config -> config.maxAgeSec(Integer.MAX_VALUE + 1L));

        // when
        final Account actualAccount = target.validate(givenAccount);

        // then
        assertThat(actualAccount).isEqualTo(fallbackAccount());
        verify(metrics).updateAlertsConfigFailed("some-id", MetricName.price_floors);
    }

    @Test
    public void validateShouldReturnGivenAccountIfTimeoutLessThanMinimumValue() {
        // given
        final Account givenAccount = accountWithFloorsFetchConfig(config -> config.timeout(9L));

        // when
        final Account actualAccount = target.validate(givenAccount);

        // then
        assertThat(actualAccount).isEqualTo(fallbackAccount());
        verify(metrics).updateAlertsConfigFailed("some-id", MetricName.price_floors);
    }

    @Test
    public void validateShouldReturnGivenAccountIfTimeoutMoreThanMaximumValue() {
        // given
        final Account givenAccount = accountWithFloorsFetchConfig(config -> config.timeout(12000L));

        // when
        final Account actualAccount = target.validate(givenAccount);

        // then
        assertThat(actualAccount).isEqualTo(fallbackAccount());
        verify(metrics).updateAlertsConfigFailed("some-id", MetricName.price_floors);
    }

    @Test
    public void validateShouldReturnGivenAccountIfMaxRulesLessThanMinimumValue() {
        // given
        final Account givenAccount = accountWithFloorsFetchConfig(config -> config.maxRules(-1L));

        // when
        final Account actualAccount = target.validate(givenAccount);

        // then
        assertThat(actualAccount).isEqualTo(fallbackAccount());
        verify(metrics).updateAlertsConfigFailed("some-id", MetricName.price_floors);
    }

    @Test
    public void validateShouldReturnGivenAccountIfMaxRulesMoreThanMaximumValue() {
        // given
        final Account givenAccount = accountWithFloorsFetchConfig(config -> config.maxRules(Integer.MAX_VALUE + 1L));

        // when
        final Account actualAccount = target.validate(givenAccount);

        // then
        assertThat(actualAccount).isEqualTo(fallbackAccount());
        verify(metrics).updateAlertsConfigFailed("some-id", MetricName.price_floors);
    }

    @Test
    public void validateShouldReturnGivenAccountIfMaxFileSizeLessThanMinimumValue() {
        // given
        final Account givenAccount = accountWithFloorsFetchConfig(config -> config.maxFileSize(-1L));

        // when
        final Account actualAccount = target.validate(givenAccount);

        // then
        assertThat(actualAccount).isEqualTo(fallbackAccount());
        verify(metrics).updateAlertsConfigFailed("some-id", MetricName.price_floors);
    }

    @Test
    public void validateShouldReturnGivenAccountIfMaxFileSizeMoreThanMaximumValue() {
        // given
        final Account givenAccount = accountWithFloorsFetchConfig(config -> config.maxFileSize(Integer.MAX_VALUE + 1L));

        // when
        final Account actualAccount = target.validate(givenAccount);

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
                .auction(AccountAuctionConfig.builder().priceFloors(null).build())
                .build();
    }
}
