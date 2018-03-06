package org.prebid.server.spring.config;

import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixListFactory;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import org.prebid.server.ImplicitParametersExtractor;
import org.prebid.server.auction.AuctionRequestFactory;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.PreBidRequestContextFactory;
import org.prebid.server.auction.StoredRequestProcessor;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.HttpAdapterConnector;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.metric.Metrics;
import org.prebid.server.optout.GoogleRecaptchaVerifier;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.validation.BidderParamValidator;
import org.prebid.server.validation.RequestValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.io.IOException;
import java.util.Properties;

@Configuration
public class ServiceConfiguration {

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    CacheService cacheService(
            @Value("${cache.scheme}") String scheme,
            @Value("${cache.host}") String host,
            @Value("${cache.query}") String query,
            HttpClient httpClient) {

        return new CacheService(
                httpClient,
                CacheService.getCacheEndpointUrl(scheme, host),
                CacheService.getCachedAssetUrlTemplate(query, scheme, host));
    }

    @Bean
    ImplicitParametersExtractor implicitParametersExtractor(PublicSuffixList psl) {
        return new ImplicitParametersExtractor(psl);
    }

    @Bean
    PreBidRequestContextFactory preBidRequestContextFactory(
            @Value("${default-timeout-ms}") long defaultTimeoutMs,
            ImplicitParametersExtractor implicitParametersExtractor,
            ApplicationSettings applicationSettings,
            UidsCookieService uidsCookieService) {

        return new PreBidRequestContextFactory(defaultTimeoutMs, implicitParametersExtractor, applicationSettings,
                uidsCookieService);
    }

    @Bean
    AuctionRequestFactory auctionRequestFactory(
            ImplicitParametersExtractor implicitParametersExtractor,
            UidsCookieService uidsCookieService) {

        return new AuctionRequestFactory(implicitParametersExtractor, uidsCookieService);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    GoogleRecaptchaVerifier googleRecaptchaVerifier(
            @Value("${recaptcha-url}") String recaptchaUrl,
            @Value("${recaptcha-secret}") String recaptchaSecret,
            HttpClient httpClient) {

        return new GoogleRecaptchaVerifier(httpClient, recaptchaUrl, recaptchaSecret);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    HttpClient httpClient(
            @Value("${http-client.max-pool-size}") int maxPoolSize,
            @Value("${http-client.connect-timeout-ms}") int connectTimeoutMs,
            Vertx vertx) {

        final HttpClientOptions options = new HttpClientOptions()
                .setMaxPoolSize(maxPoolSize)
                .setConnectTimeout(connectTimeoutMs);
        return vertx.createHttpClient(options);
    }

    @Bean
    UidsCookieService uidsCookieService(
            @Value("${host-cookie.optout-cookie.name:#{null}}") String optOutCookieName,
            @Value("${host-cookie.optout-cookie.value:#{null}}") String optOutCookieValue,
            @Value("${host-cookie.family:#{null}}") String hostCookieFamily,
            @Value("${host-cookie.cookie-name:#{null}}") String hostCookieName,
            @Value("${host-cookie.domain:#{null}}") String hostCookieDomain,
            @Value("${host-cookie.ttl-days}") Integer ttlDays) {

        return new UidsCookieService(optOutCookieName, optOutCookieValue, hostCookieFamily, hostCookieName,
                hostCookieDomain, ttlDays);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    ExchangeService exchangeService(
            @Value("${auction.expected-cache-time-ms}") long expectedCacheTimeMs,
            BidderCatalog bidderCatalog,
            CacheService cacheService, Metrics metrics) {

        return new ExchangeService(bidderCatalog, cacheService, metrics,
                expectedCacheTimeMs);
    }

    @Bean
    StoredRequestProcessor storedRequestProcessor(
            @Value("${auction.stored-requests-timeout-ms}") long defaultTimeoutMs,
            ApplicationSettings applicationSettings) {
        return new StoredRequestProcessor(applicationSettings, defaultTimeoutMs);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    HttpAdapterConnector httpAdapterConnector(HttpClient httpClient) {
        return new HttpAdapterConnector(httpClient);
    }

    @Bean
    RequestValidator requestValidator(BidderCatalog bidderCatalog,
                                      BidderParamValidator bidderParamValidator) {
        return new RequestValidator(bidderCatalog, bidderParamValidator);
    }

    @Bean
    BidderParamValidator bidderParamValidator(BidderCatalog bidderCatalog) {
        return BidderParamValidator.create(bidderCatalog, "/static/bidder-params");
    }

    @Bean
    PublicSuffixList psl() {
        final PublicSuffixListFactory factory = new PublicSuffixListFactory();

        final Properties properties = factory.getDefaults();
        properties.setProperty(PublicSuffixListFactory.PROPERTY_LIST_FILE, "/effective_tld_names.dat");
        try {
            return factory.build(properties);
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalArgumentException("Could not initialize public suffix list", e);
        }
    }
}
