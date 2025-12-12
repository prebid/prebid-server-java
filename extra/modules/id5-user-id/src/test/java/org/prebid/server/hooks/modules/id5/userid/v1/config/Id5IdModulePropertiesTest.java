package org.prebid.server.hooks.modules.id5.userid.v1.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class Id5IdModulePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestPropsConfig.class);

    @EnableConfigurationProperties(Id5IdModuleProperties.class)
    static class TestPropsConfig { }

    @Test
    void shouldHaveDefaultFetchEndpoint() {
        contextRunner
                .withPropertyValues(
                        "hooks.id5-user-id.partner=123",
                        "hooks.id5-user-id.provider-name=test-provider")
                .run(ctx -> {
                    final Id5IdModuleProperties props = ctx.getBean(Id5IdModuleProperties.class);
                    assertThat(props.getFetchEndpoint()).isEqualTo("https://api.id5-sync.com/gs/v2");
                });
    }

    @Test
    void shouldBindBidderFilterValuesWithDefaultExcludeFalse() {
        contextRunner
                .withPropertyValues(
                        "hooks.id5-user-id.partner=123",
                        "hooks.id5-user-id.provider-name=test-provider",
                        "hooks.id5-user-id.bidder-filter.values=appnexus,rubicon")
                .run(ctx -> {
                    final Id5IdModuleProperties props = ctx.getBean(Id5IdModuleProperties.class);
                    final ValuesFilter<String> filter = props.getBidderFilter();

                    assertThat(filter).isNotNull();
                    assertThat(filter.isExclude()).isFalse(); // default
                    assertThat(filter.getValues()).containsExactlyInAnyOrder("appnexus", "rubicon");
                });
    }

    @Test
    void shouldBindAccountFilterWithExcludeTrue() {
        contextRunner
                .withPropertyValues(
                        "hooks.id5-user-id.partner=123",
                        "hooks.id5-user-id.provider-name=test-provider",
                        "hooks.id5-user-id.account-filter.exclude=true",
                        "hooks.id5-user-id.account-filter.values=acc-1,acc-2")
                .run(ctx -> {
                    final Id5IdModuleProperties props = ctx.getBean(Id5IdModuleProperties.class);
                    final ValuesFilter<String> filter = props.getAccountFilter();

                    assertThat(filter).isNotNull();
                    assertThat(filter.isExclude()).isTrue();
                    assertThat(filter.getValues()).containsExactlyInAnyOrder("acc-1", "acc-2");
                });
    }

    @Test
    void shouldBindCountryFilterWhenOnlyExcludeProvidedValuesRemainNull() {
        contextRunner
                .withPropertyValues(
                        "hooks.id5-user-id.partner=123",
                        "hooks.id5-user-id.provider-name=test-provider",
                        "hooks.id5-user-id.country-filter.exclude=true")
                .run(ctx -> {
                    final Id5IdModuleProperties props = ctx.getBean(Id5IdModuleProperties.class);
                    final ValuesFilter<String> filter = props.getCountryFilter();

                    assertThat(filter).isNotNull();
                    assertThat(filter.isExclude()).isTrue();
                    assertThat(filter.getValues()).isNull();
                });
    }

    @Test
    void shouldNotCreateFiltersWhenNotProvided() {
        contextRunner
                .withPropertyValues(
                        "hooks.id5-user-id.partner=123",
                        "hooks.id5-user-id.provider-name=test-provider")
                .run(ctx -> {
                    final Id5IdModuleProperties props = ctx.getBean(Id5IdModuleProperties.class);

                    assertThat(props.getBidderFilter()).isNull();
                    assertThat(props.getAccountFilter()).isNull();
                    assertThat(props.getCountryFilter()).isNull();
                });
    }
}
