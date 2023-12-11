package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.HostVendorTcfDefinerService;
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
import org.prebid.server.privacy.gdpr.vendorlist.VendorListService;
import org.prebid.server.privacy.gdpr.vendorlist.VersionedVendorListService;
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
    VendorListService vendorListServiceV2(
            @Value("${logging.sampling-rate:0.01}") double logSamplingRate,
            @Value("${gdpr.vendorlist.v2.cache-dir}") String cacheDir,
            @Value("${gdpr.vendorlist.v2.http-endpoint-template}") String endpointTemplate,
            @Value("${gdpr.vendorlist.default-timeout-ms}") int defaultTimeoutMs,
            @Value("${gdpr.vendorlist.v2.refresh-missing-list-period-ms}") int refreshMissingListPeriodMs,
            @Value("${gdpr.vendorlist.v2.fallback-vendor-list-path:#{null}}") String fallbackVendorListPath,
            @Value("${gdpr.vendorlist.v2.deprecated}") boolean deprecated,
            Vertx vertx,
            FileSystem fileSystem,
            HttpClient httpClient,
            Metrics metrics,
            JacksonMapper mapper) {

        return new VendorListService(
                logSamplingRate,
                cacheDir,
                endpointTemplate,
                defaultTimeoutMs,
                refreshMissingListPeriodMs,
                deprecated,
                fallbackVendorListPath,
                vertx,
                fileSystem,
                httpClient,
                metrics,
                "v2",
                mapper);
    }

    @Bean
    VendorListService vendorListServiceV3(
            @Value("${logging.sampling-rate:0.01}") double logSamplingRate,
            @Value("${gdpr.vendorlist.v3.cache-dir}") String cacheDir,
            @Value("${gdpr.vendorlist.v3.http-endpoint-template}") String endpointTemplate,
            @Value("${gdpr.vendorlist.default-timeout-ms}") int defaultTimeoutMs,
            @Value("${gdpr.vendorlist.v3.refresh-missing-list-period-ms}") int refreshMissingListPeriodMs,
            @Value("${gdpr.vendorlist.v3.fallback-vendor-list-path:#{null}}") String fallbackVendorListPath,
            @Value("${gdpr.vendorlist.v3.deprecated}") boolean deprecated,
            Vertx vertx,
            FileSystem fileSystem,
            HttpClient httpClient,
            Metrics metrics,
            JacksonMapper mapper) {

        return new VendorListService(
                logSamplingRate,
                cacheDir,
                endpointTemplate,
                defaultTimeoutMs,
                refreshMissingListPeriodMs,
                deprecated,
                fallbackVendorListPath,
                vertx,
                fileSystem,
                httpClient,
                metrics,
                "v3",
                mapper);
    }

    @Bean
    VersionedVendorListService versionedVendorListService(VendorListService vendorListServiceV2,
                                                          VendorListService vendorListServiceV3) {

        return new VersionedVendorListService(vendorListServiceV2, vendorListServiceV3);
    }

    @Bean
    Tcf2Service tcf2Service(GdprConfig gdprConfig,
                            List<PurposeStrategy> purposeStrategies,
                            List<SpecialFeaturesStrategy> specialFeaturesStrategies,
                            VersionedVendorListService versionedVendorListService,
                            BidderCatalog bidderCatalog) {

        return new Tcf2Service(
                gdprConfig,
                purposeStrategies,
                specialFeaturesStrategies,
                versionedVendorListService,
                bidderCatalog);
    }

    @Bean
    TcfDefinerService tcfDefinerService(
            GdprConfig gdprConfig,
            @Value("${gdpr.eea-countries}") String eeaCountriesAsString,
            Tcf2Service tcf2Service,
            @Autowired(required = false) GeoLocationService geoLocationService,
            BidderCatalog bidderCatalog,
            IpAddressHelper ipAddressHelper,
            Metrics metrics) {

        final Set<String> eeaCountries = new HashSet<>(Arrays.asList(eeaCountriesAsString.trim().split(",")));

        return new TcfDefinerService(
                gdprConfig,
                eeaCountries,
                tcf2Service,
                geoLocationService,
                bidderCatalog,
                ipAddressHelper,
                metrics);
    }

    @Bean
    HostVendorTcfDefinerService hostVendorTcfDefinerService(
            TcfDefinerService tcfDefinerService,
            @Value("${gdpr.host-vendor-id:#{null}}") Integer hostVendorId) {

        return new HostVendorTcfDefinerService(tcfDefinerService, hostVendorId);
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
