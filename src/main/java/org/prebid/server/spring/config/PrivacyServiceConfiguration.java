package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.GdprService;
import org.prebid.server.privacy.gdpr.Tcf2Service;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.PurposeEightStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.PurposeFiveStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.PurposeFourStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.PurposeNineStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.PurposeOneStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.PurposeSevenStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.PurposeSixStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.PurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.PurposeTenStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.PurposeThreeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.PurposeTwoStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.BasicEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.FullEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.NoEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.PurposeTwoBasicEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.specialfeature.SpecialFeaturesOneStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.specialfeature.SpecialFeaturesStrategy;
import org.prebid.server.privacy.gdpr.vendorlist.VendorListServiceV1;
import org.prebid.server.privacy.gdpr.vendorlist.VendorListServiceV2;
import org.prebid.server.settings.model.GdprConfig;
import org.prebid.server.settings.model.Purpose;
import org.prebid.server.settings.model.Purposes;
import org.prebid.server.settings.model.SpecialFeature;
import org.prebid.server.settings.model.SpecialFeatures;
import org.prebid.server.vertx.http.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class PrivacyServiceConfiguration {

    @Bean
    VendorListServiceV1 vendorListServiceV1(
            @Value("${gdpr.vendorlist.v1.cache-dir}") String cacheDir,
            @Value("${gdpr.vendorlist.v1.http-endpoint-template}") String endpointTemplate,
            @Value("${gdpr.vendorlist.v1.http-default-timeout-ms}") int defaultTimeoutMs,
            @Value("${gdpr.vendorlist.v1.refresh-missing-list-period-ms}") int refreshMissingListPeriodMs,
            @Value("${gdpr.host-vendor-id:#{null}}") Integer hostVendorId,
            @Value("${gdpr.vendorlist.v1.fallback-vendor-list-path:#{null}}") String fallbackVendorListPath,
            BidderCatalog bidderCatalog,
            Vertx vertx,
            FileSystem fileSystem,
            HttpClient httpClient,
            Metrics metrics,
            JacksonMapper mapper) {

        return new VendorListServiceV1(
                cacheDir,
                endpointTemplate,
                defaultTimeoutMs,
                refreshMissingListPeriodMs,
                hostVendorId,
                fallbackVendorListPath,
                bidderCatalog,
                vertx,
                fileSystem,
                httpClient,
                metrics,
                mapper);
    }

    @Bean
    VendorListServiceV2 vendorListServiceV2(
            @Value("${gdpr.vendorlist.v2.cache-dir}") String cacheDir,
            @Value("${gdpr.vendorlist.v2.http-endpoint-template}") String endpointTemplate,
            @Value("${gdpr.vendorlist.v2.http-default-timeout-ms}") int defaultTimeoutMs,
            @Value("${gdpr.vendorlist.v2.refresh-missing-list-period-ms}") int refreshMissingListPeriodMs,
            @Value("${gdpr.host-vendor-id:#{null}}") Integer hostVendorId,
            @Value("${gdpr.vendorlist.v2.fallback-vendor-list-path:#{null}}") String fallbackVendorListPath,
            BidderCatalog bidderCatalog,
            Vertx vertx,
            FileSystem fileSystem,
            HttpClient httpClient,
            Metrics metrics,
            JacksonMapper mapper) {

        return new VendorListServiceV2(
                cacheDir,
                endpointTemplate,
                defaultTimeoutMs,
                refreshMissingListPeriodMs,
                hostVendorId,
                fallbackVendorListPath,
                bidderCatalog,
                vertx,
                fileSystem,
                httpClient,
                metrics,
                mapper);
    }

    @Bean
    GdprService gdprService(VendorListServiceV1 vendorListServiceV1) {
        return new GdprService(vendorListServiceV1);
    }

    @Bean
    Tcf2Service tcf2Service(GdprConfig gdprConfig,
                            List<PurposeStrategy> purposeStrategies,
                            List<SpecialFeaturesStrategy> specialFeaturesStrategies,
                            VendorListServiceV2 vendorListServiceV2,
                            BidderCatalog bidderCatalog) {

        return new Tcf2Service(gdprConfig, purposeStrategies, specialFeaturesStrategies, vendorListServiceV2,
                bidderCatalog);
    }

    @Bean
    TcfDefinerService tcfDefinerService(
            GdprConfig gdprConfig,
            @Value("${gdpr.eea-countries}") String eeaCountriesAsString,
            GdprService gdprService,
            Tcf2Service tcf2Service,
            @Autowired(required = false) GeoLocationService geoLocationService,
            BidderCatalog bidderCatalog,
            IpAddressHelper ipAddressHelper,
            Metrics metrics) {

        final Set<String> eeaCountries = new HashSet<>(Arrays.asList(eeaCountriesAsString.trim().split(",")));

        return new TcfDefinerService(
                gdprConfig,
                eeaCountries,
                gdprService,
                tcf2Service,
                geoLocationService,
                bidderCatalog,
                ipAddressHelper,
                metrics);
    }

    @Bean
    PurposeOneStrategy purposeOneStrategy(FullEnforcePurposeStrategy fullEnforcePurposeStrategy,
                                          BasicEnforcePurposeStrategy basicEnforcePurposeStrategy,
                                          NoEnforcePurposeStrategy noEnforcePurposeStrategy) {
        return new PurposeOneStrategy(fullEnforcePurposeStrategy, basicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);
    }

    @Bean
    PurposeTwoStrategy purposeTwoStrategy(FullEnforcePurposeStrategy fullEnforcePurposeStrategy,
                                          PurposeTwoBasicEnforcePurposeStrategy purposeTwoBasicEnforcePurposeStrategy,
                                          NoEnforcePurposeStrategy noEnforcePurposeStrategy) {
        return new PurposeTwoStrategy(fullEnforcePurposeStrategy, purposeTwoBasicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);
    }

    @Bean
    PurposeThreeStrategy purposeThreeStrategy(FullEnforcePurposeStrategy fullEnforcePurposeStrategy,
                                              BasicEnforcePurposeStrategy basicEnforcePurposeStrategy,
                                              NoEnforcePurposeStrategy noEnforcePurposeStrategy) {
        return new PurposeThreeStrategy(fullEnforcePurposeStrategy, basicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);
    }

    @Bean
    PurposeFourStrategy purposeFourStrategy(FullEnforcePurposeStrategy fullEnforcePurposeStrategy,
                                            BasicEnforcePurposeStrategy basicEnforcePurposeStrategy,
                                            NoEnforcePurposeStrategy noEnforcePurposeStrategy) {
        return new PurposeFourStrategy(fullEnforcePurposeStrategy, basicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);
    }

    @Bean
    PurposeFiveStrategy purposeFiveStrategy(FullEnforcePurposeStrategy fullEnforcePurposeStrategy,
                                            BasicEnforcePurposeStrategy basicEnforcePurposeStrategy,
                                            NoEnforcePurposeStrategy noEnforcePurposeStrategy) {
        return new PurposeFiveStrategy(fullEnforcePurposeStrategy, basicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);
    }

    @Bean
    PurposeSixStrategy purposeSixStrategy(FullEnforcePurposeStrategy fullEnforcePurposeStrategy,
                                          BasicEnforcePurposeStrategy basicEnforcePurposeStrategy,
                                          NoEnforcePurposeStrategy noEnforcePurposeStrategy) {
        return new PurposeSixStrategy(fullEnforcePurposeStrategy, basicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);
    }

    @Bean
    PurposeSevenStrategy purposeSevenStrategy(FullEnforcePurposeStrategy fullEnforcePurposeStrategy,
                                              BasicEnforcePurposeStrategy basicEnforcePurposeStrategy,
                                              NoEnforcePurposeStrategy noEnforcePurposeStrategy) {
        return new PurposeSevenStrategy(fullEnforcePurposeStrategy, basicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);
    }

    @Bean
    PurposeEightStrategy purposeEightStrategy(FullEnforcePurposeStrategy fullEnforcePurposeStrategy,
                                              BasicEnforcePurposeStrategy basicEnforcePurposeStrategy,
                                              NoEnforcePurposeStrategy noEnforcePurposeStrategy) {
        return new PurposeEightStrategy(fullEnforcePurposeStrategy, basicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);
    }

    @Bean
    PurposeNineStrategy purposeNineStrategy(FullEnforcePurposeStrategy fullEnforcePurposeStrategy,
                                            BasicEnforcePurposeStrategy basicEnforcePurposeStrategy,
                                            NoEnforcePurposeStrategy noEnforcePurposeStrategy) {
        return new PurposeNineStrategy(fullEnforcePurposeStrategy, basicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);
    }

    @Bean
    PurposeTenStrategy purposeTenStrategy(FullEnforcePurposeStrategy fullEnforcePurposeStrategy,
                                          BasicEnforcePurposeStrategy basicEnforcePurposeStrategy,
                                          NoEnforcePurposeStrategy noEnforcePurposeStrategy) {
        return new PurposeTenStrategy(fullEnforcePurposeStrategy, basicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);
    }

    @Bean
    SpecialFeaturesOneStrategy specialFeaturesOneStrategy() {
        return new SpecialFeaturesOneStrategy();
    }

    @Bean
    FullEnforcePurposeStrategy fullEnforcePurposeStrategy() {
        return new FullEnforcePurposeStrategy();
    }

    @Bean
    BasicEnforcePurposeStrategy basicEnforcePurposeStrategy() {
        return new BasicEnforcePurposeStrategy();
    }

    @Bean
    PurposeTwoBasicEnforcePurposeStrategy purposeTwoBasicEnforcePurposeStrategy() {
        return new PurposeTwoBasicEnforcePurposeStrategy();
    }

    @Bean
    NoEnforcePurposeStrategy noEnforcePurposeStrategy() {
        return new NoEnforcePurposeStrategy();
    }

    @Bean
    @ConfigurationProperties(prefix = "gdpr")
    GdprConfig gdprConfig() {
        return new GdprConfig();
    }

    @Bean
    @ConfigurationProperties(prefix = "gdpr.purposes")
    Purposes purposes() {
        return new Purposes();
    }

    @Bean
    Purpose purpose() {
        return new Purpose();
    }

    @Bean
    @ConfigurationProperties(prefix = "gdpr.special-features")
    SpecialFeatures specialFeatures() {
        return new SpecialFeatures();
    }

    @Bean
    SpecialFeature specialFeature() {
        return new SpecialFeature();
    }
}
