package org.prebid.server.hooks.modules.id5.userid.config;

import org.prebid.server.hooks.modules.id5.userid.v1.Id5IdFetchHook;
import org.prebid.server.hooks.modules.id5.userid.v1.Id5IdInjectHook;
import org.prebid.server.hooks.modules.id5.userid.v1.Id5IdModule;
import org.prebid.server.hooks.modules.id5.userid.v1.config.Id5IdModuleProperties;
import org.prebid.server.hooks.modules.id5.userid.v1.fetch.HttpFetchClient;
import org.prebid.server.hooks.modules.id5.userid.v1.filter.AccountFetchFilter;
import org.prebid.server.hooks.modules.id5.userid.v1.filter.CountryFetchFilter;
import org.prebid.server.hooks.modules.id5.userid.v1.filter.FetchActionFilter;
import org.prebid.server.hooks.modules.id5.userid.v1.filter.InjectActionFilter;
import org.prebid.server.hooks.modules.id5.userid.v1.filter.SamplingFetchFilter;
import org.prebid.server.hooks.modules.id5.userid.v1.filter.SelectedBidderFilter;
import org.prebid.server.hooks.modules.id5.userid.v1.model.ConstantId5PartnerId;
import org.prebid.server.hooks.modules.id5.userid.v1.model.Id5PartnerIdProvider;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.util.VersionInfo;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.time.Clock;
import java.util.List;
import java.util.Random;

@Configuration
@EnableConfigurationProperties(Id5IdModuleProperties.class)
@ConditionalOnProperty(prefix = "hooks." + Id5IdModule.CODE, name = "enabled", havingValue = "true")
@PropertySource(
        value = "classpath:/module-config/id5-user-id.yaml",
        factory = YamlPropertySourceFactory.class)
public class Id5UserIdModuleConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(Id5UserIdModuleConfiguration.class);

    @Bean
    @ConditionalOnProperty(prefix = "hooks." + Id5IdModule.CODE, name = "fetch-sampling-rate")
    SamplingFetchFilter fetchSampler(Id5IdModuleProperties properties) {
        LOG.debug("id5-user-id-fetch-sampling-rate enabled with rate {}", properties.getFetchSamplingRate());
        return new SamplingFetchFilter(new Random(), properties.getFetchSamplingRate());
    }

    @Bean
    @ConditionalOnProperty(prefix = "hooks." + Id5IdModule.CODE, name = "bidder-filter.values")
    SelectedBidderFilter selectedBidderFilter(Id5IdModuleProperties properties) {
        LOG.debug("id5-user-id-bidder-filter enabled, {}", properties.getBidderFilter());
        return new SelectedBidderFilter(properties.getBidderFilter());
    }

    @Bean
    @ConditionalOnProperty(prefix = "hooks." + Id5IdModule.CODE, name = "account-filter.values")
    AccountFetchFilter accountFetchFilter(Id5IdModuleProperties properties) {
        LOG.debug("id5-user-id-account-filter enabled, {}", properties.getAccountFilter());
        return new AccountFetchFilter(properties.getAccountFilter());
    }

    @Bean
    @ConditionalOnProperty(prefix = "hooks." + Id5IdModule.CODE, name = "country-filter.values")
    CountryFetchFilter countryFetchFilter(Id5IdModuleProperties properties) {
        LOG.debug("id5-user-id-country-filter enabled, {}", properties.getCountryFilter());
        return new CountryFetchFilter(properties.getCountryFilter());
    }

    @Bean
    @ConditionalOnMissingBean(Id5PartnerIdProvider.class)
    Id5PartnerIdProvider constantId5PartnerIdProvider(Id5IdModuleProperties properties) {
        final Long partnerId = properties.getPartner();
        if (partnerId == null || partnerId < 1) {
            throw new IllegalArgumentException(
                    "hooks.id5-user-id.partner is required and must be >= 1 when using default partner ID provider");
        }
        return new ConstantId5PartnerId(partnerId);
    }

    @Bean
    HttpFetchClient fetchClient(Id5IdModuleProperties properties,
                                VersionInfo versionInfo,
                                HttpClient httpClient,
                                JacksonMapper jacksonMapper,
                                Clock clock) {

        LOG.debug("id5-user-id-fetch hook enabled, endpoint: {}", properties.getFetchEndpoint());
        return new HttpFetchClient(
                properties.getFetchEndpoint(),
                httpClient,
                jacksonMapper,
                clock,
                versionInfo,
                properties);
    }

    @Bean
    Id5IdFetchHook id5UserIdFetchHook(HttpFetchClient fetchClient,
                                      List<FetchActionFilter> filters,
                                      Id5PartnerIdProvider id5PartnerIdProvider) {

        return new Id5IdFetchHook(fetchClient, filters, id5PartnerIdProvider);
    }

    @Bean
    Id5IdInjectHook id5UserIdInjectHook(Id5IdModuleProperties properties,
                                        List<InjectActionFilter> injectFilters) {

        LOG.debug("id5-user-id-inject hook enabled");
        return new Id5IdInjectHook(properties.getInserterName(), injectFilters);
    }

    @Bean
    Id5IdModule id5UserIdModule(Id5IdFetchHook fetchHook, Id5IdInjectHook injectHook) {
        return new Id5IdModule(List.of(fetchHook, injectHook));
    }
}
