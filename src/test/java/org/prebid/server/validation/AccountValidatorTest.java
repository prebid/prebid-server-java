package org.prebid.server.validation;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.exception.InvalidAccountConfigException;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;
import org.prebid.server.settings.model.AccountPriceFloorsFetchConfig;

import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

public class AccountValidatorTest {

    private AccountValidator testingInstance;

    @Before
    public void setUp() {
        testingInstance = new AccountValidator();
    }

    @Test
    public void fetchAccountShouldReturnFailedFutureIfEnforceFloorsRateLessThanMinimumValue() {
        // given
        final Account givenAccount = Account.builder()
                .id("some-id")
                .auction(AccountAuctionConfig.builder()
                        .priceFloors(AccountPriceFloorsConfig.builder()
                                .enforceFloorsRate(-1).build())
                        .build())
                .build();

        // when
        final Future<?> future = testingInstance.validateAccountConfig(givenAccount);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidAccountConfigException.class)
                .hasMessage("Account with id 'some-id' has invalid config: "
                        + "Invalid price-floors property 'enforce-floors-rate', value passed: -1");
    }

    @Test
    public void fetchAccountShouldReturnFailedFutureIfEnforceFloorsRateMoreThanMaximumValue() {
        // given
        final Account givenAccount = Account.builder()
                .id("some-id")
                .auction(AccountAuctionConfig.builder()
                        .priceFloors(AccountPriceFloorsConfig.builder()
                                .enforceFloorsRate(101).build())
                        .build())
                .build();

        // when
        final Future<?> future = testingInstance.validateAccountConfig(givenAccount);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidAccountConfigException.class)
                .hasMessage("Account with id 'some-id' has invalid config: "
                        + "Invalid price-floors property 'enforce-floors-rate', value passed: 101");
    }

    @Test
    public void fetchAccountShouldReturnFailedFutureIfPeriodicSecLessThanMinimumValue() {
        // when
        final Future<?> future = testingInstance.validateAccountConfig(
                accountWithFloorsFetchConfig(config -> config.periodSec(200L)));

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidAccountConfigException.class)
                .hasMessage("Account with id 'some-id' has invalid config: "
                        + "Invalid price-floors property 'period-sec', value passed: 200");
    }

    @Test
    public void fetchAccountShouldReturnFailedFutureIfPeriodicSecMoreThanMaxAgeSec() {
        // when
        final Future<?> future = testingInstance.validateAccountConfig(accountWithFloorsFetchConfig(config ->
                config.periodSec(900L).maxAgeSec(800L)));

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidAccountConfigException.class)
                .hasMessage("Account with id 'some-id' has invalid config: "
                        + "Invalid price-floors property 'period-sec', value passed: 900");
    }

    @Test
    public void fetchAccountShouldReturnFailedFutureIfMaxAgeSecLessThanMinimumValue() {
        // when
        final Future<?> future = testingInstance.validateAccountConfig(
                accountWithFloorsFetchConfig(config -> config.maxAgeSec(500L)));

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidAccountConfigException.class)
                .hasMessage("Account with id 'some-id' has invalid config: "
                        + "Invalid price-floors property 'max-age-sec', value passed: 500");
    }

    @Test
    public void fetchAccountShouldReturnFailedFutureIfMaxAgeSecMoreThanMaximumValue() {
        // when
        final Future<?> future = testingInstance.validateAccountConfig(
                accountWithFloorsFetchConfig(config -> config.maxAgeSec(Integer.MAX_VALUE + 1L)));

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidAccountConfigException.class)
                .hasMessage(
                        "Account with id 'some-id' has invalid config: "
                                + "Invalid price-floors property 'max-age-sec', value passed: 2147483648");
    }

    @Test
    public void fetchAccountShouldReturnFailedFutureIfTimeoutLessThanMinimumValue() {
        // when
        final Future<?> future = testingInstance.validateAccountConfig(
                accountWithFloorsFetchConfig(config -> config.timeout(9L)));

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidAccountConfigException.class)
                .hasMessage("Account with id 'some-id' has invalid config: "
                        + "Invalid price-floors property 'timeout-ms', value passed: 9");
    }

    @Test
    public void fetchAccountShouldReturnFailedFutureIfTimeoutMoreThanMaximumValue() {
        // when
        final Future<?> future = testingInstance.validateAccountConfig(
                accountWithFloorsFetchConfig(config -> config.timeout(12000L)));

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidAccountConfigException.class)
                .hasMessage(
                        "Account with id 'some-id' has invalid config: "
                                + "Invalid price-floors property 'timeout-ms', value passed: 12000");
    }

    @Test
    public void fetchAccountShouldReturnFailedFutureIfMaxRulesLessThanMinimumValue() {
        // when
        final Future<?> future = testingInstance.validateAccountConfig(
                accountWithFloorsFetchConfig(config -> config.maxRules(-1L)));

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidAccountConfigException.class)
                .hasMessage("Account with id 'some-id' has invalid config: "
                        + "Invalid price-floors property 'max-rules', value passed: -1");
    }

    @Test
    public void fetchAccountShouldReturnFailedFutureIfMaxRulesMoreThanMaximumValue() {
        // when
        final Future<?> future = testingInstance.validateAccountConfig(
                accountWithFloorsFetchConfig(config -> config.maxRules(Integer.MAX_VALUE + 1L)));

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidAccountConfigException.class)
                .hasMessage(
                        "Account with id 'some-id' has invalid config: "
                                + "Invalid price-floors property 'max-rules', value passed: 2147483648");
    }

    @Test
    public void fetchAccountShouldReturnFailedFutureIfMaxFileSizeLessThanMinimumValue() {
        // when
        final Future<?> future = testingInstance.validateAccountConfig(
                accountWithFloorsFetchConfig(config -> config.maxFileSize(-1L)));

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidAccountConfigException.class)
                .hasMessage("Account with id 'some-id' has invalid config: "
                        + "Invalid price-floors property 'max-file-size-kb', value passed: -1");
    }

    @Test
    public void fetchAccountShouldReturnFailedFutureIfMaxFileSizeMoreThanMaximumValue() {
        // when
        final Future<?> future = testingInstance.validateAccountConfig(
                accountWithFloorsFetchConfig(config -> config.maxFileSize(Integer.MAX_VALUE + 1L)));

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidAccountConfigException.class)
                .hasMessage(
                        "Account with id 'some-id' has invalid config: Invalid price-floors property "
                                + "'max-file-size-kb', value passed: 2147483648");
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
}
