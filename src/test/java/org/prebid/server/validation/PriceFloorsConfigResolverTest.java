package org.prebid.server.validation;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.floors.PriceFloorsConfigResolver;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;
import org.prebid.server.settings.model.AccountPriceFloorsFetchConfig;

import java.util.function.UnaryOperator;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;

public class PriceFloorsConfigResolverTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Metrics metrics;

    private PriceFloorsConfigResolver testingInstance;

    @Before
    public void setUp() {
        testingInstance = new PriceFloorsConfigResolver(
                jacksonMapper.encodeToString(withDefaultFloorsConfig()),
                metrics);
    }

    @Test
    public void updateFloorsConfigShouldNotChangeAccountIfConfigIsValid() {
        // when
        final Account account = accountWithFloorsFetchConfig(identity());
        final Future<?> future = testingInstance.updateFloorsConfig(account);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isSameAs(account);
    }

    @Test
    public void updateFloorsConfigShouldReturnDefaultConfigIfEnforceFloorsRateLessThanMinimumValue() {
        // given
        final Account givenAccount = Account.builder()
                .id("some-id")
                .auction(AccountAuctionConfig.builder()
                        .priceFloors(AccountPriceFloorsConfig.builder()
                                .enforceFloorsRate(-1).build())
                        .build())
                .build();

        // when
        final Future<?> future = testingInstance.updateFloorsConfig(givenAccount);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result())
                .isEqualTo(withDefaultFloorsConfig().toBuilder().id("some-id").build());
    }

    @Test
    public void updateFloorsConfigShouldReturnDefaultConfigIfEnforceFloorsRateMoreThanMaximumValue() {
        // given
        final Account givenAccount = Account.builder()
                .id("some-id")
                .auction(AccountAuctionConfig.builder()
                        .priceFloors(AccountPriceFloorsConfig.builder()
                                .enforceFloorsRate(101).build())
                        .build())
                .build();

        // when
        final Future<?> future = testingInstance.updateFloorsConfig(givenAccount);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result())
                .isEqualTo(withDefaultFloorsConfig().toBuilder().id("some-id").build());
    }

    @Test
    public void updateFloorsConfigShouldReturnDefaultConfigIfPeriodicSecLessThanMinimumValue() {
        // when
        final Future<?> future = testingInstance.updateFloorsConfig(
                accountWithFloorsFetchConfig(config -> config.periodSec(200L)));

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result())
                .isEqualTo(withDefaultFloorsConfig().toBuilder().id("some-id").build());
    }

    @Test
    public void updateFloorsConfigShouldReturnDefaultConfigIfPeriodicSecMoreThanMaxAgeSec() {
        // when
        final Future<?> future = testingInstance.updateFloorsConfig(accountWithFloorsFetchConfig(config ->
                config.periodSec(900L).maxAgeSec(800L)));

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result())
                .isEqualTo(withDefaultFloorsConfig().toBuilder().id("some-id").build());
    }

    @Test
    public void updateFloorsConfigShouldReturnDefaultConfigIfMaxAgeSecLessThanMinimumValue() {
        // when
        final Future<?> future = testingInstance.updateFloorsConfig(
                accountWithFloorsFetchConfig(config -> config.maxAgeSec(500L)));

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result())
                .isEqualTo(withDefaultFloorsConfig().toBuilder().id("some-id").build());
    }

    @Test
    public void updateFloorsConfigShouldReturnDefaultConfigIfMaxAgeSecMoreThanMaximumValue() {
        // when
        final Future<?> future = testingInstance.updateFloorsConfig(
                accountWithFloorsFetchConfig(config -> config.maxAgeSec(Integer.MAX_VALUE + 1L)));

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result())
                .isEqualTo(withDefaultFloorsConfig().toBuilder().id("some-id").build());
    }

    @Test
    public void updateFloorsConfigShouldReturnDefaultConfigIfTimeoutLessThanMinimumValue() {
        // when
        final Future<?> future = testingInstance.updateFloorsConfig(
                accountWithFloorsFetchConfig(config -> config.timeout(9L)));

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result())
                .isEqualTo(withDefaultFloorsConfig().toBuilder().id("some-id").build());
    }

    @Test
    public void updateFloorsConfigShouldReturnDefaultConfigIfTimeoutMoreThanMaximumValue() {
        // when
        final Future<?> future = testingInstance.updateFloorsConfig(
                accountWithFloorsFetchConfig(config -> config.timeout(12000L)));

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result())
                .isEqualTo(withDefaultFloorsConfig().toBuilder().id("some-id").build());
    }

    @Test
    public void updateFloorsConfigShouldReturnDefaultConfigIfMaxRulesLessThanMinimumValue() {
        // when
        final Future<?> future = testingInstance.updateFloorsConfig(
                accountWithFloorsFetchConfig(config -> config.maxRules(-1L)));

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result())
                .isEqualTo(withDefaultFloorsConfig().toBuilder().id("some-id").build());
    }

    @Test
    public void updateFloorsConfigShouldReturnDefaultConfigIfMaxRulesMoreThanMaximumValue() {
        // when
        final Future<?> future = testingInstance.updateFloorsConfig(
                accountWithFloorsFetchConfig(config -> config.maxRules(Integer.MAX_VALUE + 1L)));

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result())
                .isEqualTo(withDefaultFloorsConfig().toBuilder().id("some-id").build());
    }

    @Test
    public void updateFloorsConfigShouldReturnDefaultConfigIfMaxFileSizeLessThanMinimumValue() {
        // when
        final Future<?> future = testingInstance.updateFloorsConfig(
                accountWithFloorsFetchConfig(config -> config.maxFileSize(-1L)));

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result())
                .isEqualTo(withDefaultFloorsConfig().toBuilder().id("some-id").build());
    }

    @Test
    public void updateFloorsConfigShouldReturnDefaultConfigIfMaxFileSizeMoreThanMaximumValue() {
        // when
        final Future<?> future = testingInstance.updateFloorsConfig(
                accountWithFloorsFetchConfig(config -> config.maxFileSize(Integer.MAX_VALUE + 1L)));

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result())
                .isEqualTo(withDefaultFloorsConfig().toBuilder().id("some-id").build());
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
                                                .maxFileSize(100L)
                                ).build())
                                .build())
                        .build())
                .build();
    }

    private static Account withDefaultFloorsConfig() {
        return Account.builder()
                .id("default-account-id")
                .auction(AccountAuctionConfig.builder()
                        .priceFloors(AccountPriceFloorsConfig.builder()
                                .enforceFloorsRate(20)
                                .build())
                        .build())
                .build();
    }
}
