package org.prebid.server.hooks.modules.id5.userid.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.prebid.server.hooks.modules.id5.userid.v1.Id5IdFetchHook;
import org.prebid.server.hooks.modules.id5.userid.v1.Id5IdInjectHook;
import org.prebid.server.hooks.modules.id5.userid.v1.Id5IdModule;
import org.prebid.server.hooks.modules.id5.userid.v1.filter.AccountFetchFilter;
import org.prebid.server.hooks.modules.id5.userid.v1.filter.CountryFetchFilter;
import org.prebid.server.hooks.modules.id5.userid.v1.filter.SamplingFetchFilter;
import org.prebid.server.hooks.modules.id5.userid.v1.filter.SelectedBidderFilter;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.util.VersionInfo;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class Id5UserIdModuleConfigurationTest {

    private ApplicationContextRunner contextRunner() {
        final VersionInfo versionInfo = Mockito.mock(VersionInfo.class);
        Mockito.when(versionInfo.getVersion()).thenReturn("1.2.3");

        final HttpClient httpClient = Mockito.mock(HttpClient.class);
        final JacksonMapper jacksonMapper = new JacksonMapper(new ObjectMapper());

        return new ApplicationContextRunner()
                .withBean(VersionInfo.class, () -> versionInfo)
                .withBean(HttpClient.class, () -> httpClient)
                .withBean(JacksonMapper.class, () -> jacksonMapper)
                .withBean(Clock.class, Clock::systemUTC)
                .withUserConfiguration(Id5UserIdModuleConfiguration.class);
    }

    @Test
    void shouldNotLoadConfigurationWhenModuleDisabled() {
        contextRunner()
                .withPropertyValues(
                        "hooks.id5-user-id.enabled=false",
                        "hooks.id5-user-id.partner=1",
                        "hooks.id5-user-id.provider-name=test-provider")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(Id5IdModule.class);
                    assertThat(context).doesNotHaveBean(Id5IdFetchHook.class);
                    assertThat(context).doesNotHaveBean(Id5IdInjectHook.class);
                    assertThat(context).doesNotHaveBean(SamplingFetchFilter.class);
                    assertThat(context).doesNotHaveBean(SelectedBidderFilter.class);
                    assertThat(context).doesNotHaveBean(AccountFetchFilter.class);
                    assertThat(context).doesNotHaveBean(CountryFetchFilter.class);
                });
    }

    @Test
    void shouldCreateMainBeansWhenEnabledWithoutFilters() {
        contextRunner()
                .withPropertyValues(
                        "hooks.id5-user-id.enabled=true",
                        "hooks.id5-user-id.partner=1",
                        "hooks.id5-user-id.provider-name=test-provider")
                .run(context -> {
                    assertThat(context).hasSingleBean(Id5IdModule.class);
                    assertThat(context).hasSingleBean(Id5IdFetchHook.class);
                    assertThat(context).hasSingleBean(Id5IdInjectHook.class);

                    assertThat(context).doesNotHaveBean(SamplingFetchFilter.class);
                    assertThat(context).doesNotHaveBean(SelectedBidderFilter.class);
                    assertThat(context).doesNotHaveBean(AccountFetchFilter.class);
                    assertThat(context).doesNotHaveBean(CountryFetchFilter.class);
                });
    }

    @Test
    void shouldCreateSamplingFetchFilterWhenSamplingRatePropertyPresent() {
        contextRunner()
                .withPropertyValues(
                        "hooks.id5-user-id.enabled=true",
                        "hooks.id5-user-id.partner=1",
                        "hooks.id5-user-id.provider-name=test-provider",
                        "hooks.id5-user-id.fetch-sampling-rate=0.5")
                .run(context -> assertThat(context).hasSingleBean(SamplingFetchFilter.class));
    }

    @Test
    void shouldCreateSelectedBidderFilterWhenBidderFilterValuesPresent() {
        contextRunner()
                .withPropertyValues(
                        "hooks.id5-user-id.enabled=true",
                        "hooks.id5-user-id.partner=1",
                        "hooks.id5-user-id.provider-name=test-provider",
                        "hooks.id5-user-id.bidder-filter.values=appnexus,rubicon")
                .run(context -> assertThat(context).hasSingleBean(SelectedBidderFilter.class));
    }

    @Test
    void shouldCreateAccountFetchFilterWhenAccountFilterValuesPresent() {
        contextRunner()
                .withPropertyValues(
                        "hooks.id5-user-id.enabled=true",
                        "hooks.id5-user-id.partner=1",
                        "hooks.id5-user-id.provider-name=test-provider",
                        "hooks.id5-user-id.account-filter.values=acc-1,acc-2")
                .run(context -> assertThat(context).hasSingleBean(AccountFetchFilter.class));
    }

    @Test
    void shouldCreateCountryFetchFilterWhenCountryFilterValuesPresent() {
        contextRunner()
                .withPropertyValues(
                        "hooks.id5-user-id.enabled=true",
                        "hooks.id5-user-id.partner=1",
                        "hooks.id5-user-id.provider-name=test-provider",
                        "hooks.id5-user-id.country-filter.values=US,PL")
                .run(context -> assertThat(context).hasSingleBean(CountryFetchFilter.class));
    }

    @Test
    void shouldCreateAllFiltersWhenAllPropertiesPresent() {
        contextRunner()
                .withPropertyValues(
                        "hooks.id5-user-id.enabled=true",
                        "hooks.id5-user-id.partner=1",
                        "hooks.id5-user-id.fetch-sampling-rate=1.0",
                        "hooks.id5-user-id.bidder-filter.values=appnexus",
                        "hooks.id5-user-id.account-filter.values=acc-1",
                        "hooks.id5-user-id.provider-name=test-provider",
                        "hooks.id5-user-id.country-filter.values=US")
                .run(context -> {
                    assertThat(context).hasSingleBean(SamplingFetchFilter.class);
                    assertThat(context).hasSingleBean(SelectedBidderFilter.class);
                    assertThat(context).hasSingleBean(AccountFetchFilter.class);
                    assertThat(context).hasSingleBean(CountryFetchFilter.class);
                });
    }
}
