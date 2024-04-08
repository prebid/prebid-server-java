package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import lombok.Data;
import org.prebid.server.auction.GeoLocationServiceWrapper;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.auction.privacy.enforcement.ActivityEnforcement;
import org.prebid.server.auction.privacy.enforcement.CcpaEnforcement;
import org.prebid.server.auction.privacy.enforcement.CoppaEnforcement;
import org.prebid.server.auction.privacy.enforcement.TcfEnforcement;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdActivityMask;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdCcpaMask;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdCoppaMask;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdTcfMask;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.HostVendorTcfDefinerService;
import org.prebid.server.privacy.gdpr.Tcf2Service;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.Purpose01Strategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.Purpose02Strategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.Purpose03Strategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.Purpose04Strategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.Purpose05Strategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.Purpose06Strategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.Purpose07Strategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.Purpose08Strategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.Purpose09Strategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.Purpose10Strategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.PurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.BasicEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.FullEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.NoEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.PurposeTwoBasicEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.specialfeature.SpecialFeaturesOneStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.specialfeature.SpecialFeaturesStrategy;
import org.prebid.server.privacy.gdpr.vendorlist.VendorListFetchThrottler;
import org.prebid.server.privacy.gdpr.vendorlist.VendorListService;
import org.prebid.server.privacy.gdpr.vendorlist.VersionedVendorListService;
import org.prebid.server.settings.model.GdprConfig;
import org.prebid.server.settings.model.Purpose;
import org.prebid.server.settings.model.Purposes;
import org.prebid.server.settings.model.SpecialFeature;
import org.prebid.server.settings.model.SpecialFeatures;
import org.prebid.server.spring.config.retry.RetryPolicyConfigurationProperties;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.Clock;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class PrivacyServiceConfiguration {

    @Bean
    VendorListService vendorListServiceV2(
            @Value("${logging.sampling-rate:0.01}") double logSamplingRate,
            @Value("${gdpr.vendorlist.default-timeout-ms}") int defaultTimeoutMs,
            VendorListServiceConfigurationProperties vendorListServiceV2Properties,
            Vertx vertx,
            Clock clock,
            FileSystem fileSystem,
            HttpClient httpClient,
            Metrics metrics,
            JacksonMapper mapper) {

        return new VendorListService(
                logSamplingRate,
                vendorListServiceV2Properties.getCacheDir(),
                vendorListServiceV2Properties.getHttpEndpointTemplate(),
                defaultTimeoutMs,
                vendorListServiceV2Properties.getRefreshMissingListPeriodMs(),
                vendorListServiceV2Properties.getDeprecated(),
                vendorListServiceV2Properties.getFallbackVendorListPath(),
                vertx,
                fileSystem,
                httpClient,
                metrics,
                "v2",
                mapper,
                new VendorListFetchThrottler(vendorListServiceV2Properties.getRetryPolicy().toPolicy(), clock));
    }

    @Bean
    @ConfigurationProperties(prefix = "gdpr.vendorlist.v2")
    VendorListServiceConfigurationProperties vendorListServiceV2Properties() {
        return new VendorListServiceConfigurationProperties();
    }

    @Bean
    VendorListService vendorListServiceV3(
            @Value("${logging.sampling-rate:0.01}") double logSamplingRate,
            @Value("${gdpr.vendorlist.default-timeout-ms}") int defaultTimeoutMs,
            VendorListServiceConfigurationProperties vendorListServiceV3Properties,
            Vertx vertx,
            Clock clock,
            FileSystem fileSystem,
            HttpClient httpClient,
            Metrics metrics,
            JacksonMapper mapper) {

        return new VendorListService(
                logSamplingRate,
                vendorListServiceV3Properties.getCacheDir(),
                vendorListServiceV3Properties.getHttpEndpointTemplate(),
                defaultTimeoutMs,
                vendorListServiceV3Properties.getRefreshMissingListPeriodMs(),
                vendorListServiceV3Properties.getDeprecated(),
                vendorListServiceV3Properties.getFallbackVendorListPath(),
                vertx,
                fileSystem,
                httpClient,
                metrics,
                "v3",
                mapper,
                new VendorListFetchThrottler(vendorListServiceV3Properties.getRetryPolicy().toPolicy(), clock));
    }

    @Bean
    @ConfigurationProperties(prefix = "gdpr.vendorlist.v3")
    VendorListServiceConfigurationProperties vendorListServiceV3Properties() {
        return new VendorListServiceConfigurationProperties();
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
            GeoLocationServiceWrapper geoLocationServiceWrapper,
            BidderCatalog bidderCatalog,
            IpAddressHelper ipAddressHelper,
            Metrics metrics) {

        final Set<String> eeaCountries = new HashSet<>(Arrays.asList(eeaCountriesAsString.trim().split(",")));

        return new TcfDefinerService(
                gdprConfig,
                eeaCountries,
                tcf2Service,
                geoLocationServiceWrapper,
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
    Purpose01Strategy purpose01Strategy(FullEnforcePurposeStrategy fullEnforcePurposeStrategy,
                                        BasicEnforcePurposeStrategy basicEnforcePurposeStrategy,
                                        NoEnforcePurposeStrategy noEnforcePurposeStrategy) {

        return new Purpose01Strategy(
                fullEnforcePurposeStrategy,
                basicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);
    }

    @Bean
    Purpose02Strategy purpose02Strategy(FullEnforcePurposeStrategy fullEnforcePurposeStrategy,
                                        PurposeTwoBasicEnforcePurposeStrategy purposeTwoBasicEnforcePurposeStrategy,
                                        NoEnforcePurposeStrategy noEnforcePurposeStrategy) {

        return new Purpose02Strategy(
                fullEnforcePurposeStrategy,
                purposeTwoBasicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);
    }

    @Bean
    Purpose03Strategy purpose03Strategy(FullEnforcePurposeStrategy fullEnforcePurposeStrategy,
                                        BasicEnforcePurposeStrategy basicEnforcePurposeStrategy,
                                        NoEnforcePurposeStrategy noEnforcePurposeStrategy) {

        return new Purpose03Strategy(
                fullEnforcePurposeStrategy,
                basicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);
    }

    @Bean
    Purpose04Strategy purpose04Strategy(FullEnforcePurposeStrategy fullEnforcePurposeStrategy,
                                        BasicEnforcePurposeStrategy basicEnforcePurposeStrategy,
                                        NoEnforcePurposeStrategy noEnforcePurposeStrategy) {

        return new Purpose04Strategy(
                fullEnforcePurposeStrategy,
                basicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);
    }

    @Bean
    Purpose05Strategy purpose05Strategy(FullEnforcePurposeStrategy fullEnforcePurposeStrategy,
                                        BasicEnforcePurposeStrategy basicEnforcePurposeStrategy,
                                        NoEnforcePurposeStrategy noEnforcePurposeStrategy) {

        return new Purpose05Strategy(
                fullEnforcePurposeStrategy,
                basicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);
    }

    @Bean
    Purpose06Strategy purpose06Strategy(FullEnforcePurposeStrategy fullEnforcePurposeStrategy,
                                        BasicEnforcePurposeStrategy basicEnforcePurposeStrategy,
                                        NoEnforcePurposeStrategy noEnforcePurposeStrategy) {

        return new Purpose06Strategy(
                fullEnforcePurposeStrategy,
                basicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);
    }

    @Bean
    Purpose07Strategy purpose07Strategy(FullEnforcePurposeStrategy fullEnforcePurposeStrategy,
                                        BasicEnforcePurposeStrategy basicEnforcePurposeStrategy,
                                        NoEnforcePurposeStrategy noEnforcePurposeStrategy) {

        return new Purpose07Strategy(
                fullEnforcePurposeStrategy,
                basicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);
    }

    @Bean
    Purpose08Strategy purpose08Strategy(FullEnforcePurposeStrategy fullEnforcePurposeStrategy,
                                        BasicEnforcePurposeStrategy basicEnforcePurposeStrategy,
                                        NoEnforcePurposeStrategy noEnforcePurposeStrategy) {

        return new Purpose08Strategy(
                fullEnforcePurposeStrategy,
                basicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);
    }

    @Bean
    Purpose09Strategy purpose09Strategy(FullEnforcePurposeStrategy fullEnforcePurposeStrategy,
                                        BasicEnforcePurposeStrategy basicEnforcePurposeStrategy,
                                        NoEnforcePurposeStrategy noEnforcePurposeStrategy) {

        return new Purpose09Strategy(
                fullEnforcePurposeStrategy,
                basicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);
    }

    @Bean
    Purpose10Strategy purpose10Strategy(FullEnforcePurposeStrategy fullEnforcePurposeStrategy,
                                        BasicEnforcePurposeStrategy basicEnforcePurposeStrategy,
                                        NoEnforcePurposeStrategy noEnforcePurposeStrategy) {

        return new Purpose10Strategy(
                fullEnforcePurposeStrategy,
                basicEnforcePurposeStrategy,
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

    @Bean
    UserFpdActivityMask userFpdActivityMask(UserFpdTcfMask userFpdTcfMask) {
        return new UserFpdActivityMask(userFpdTcfMask);
    }

    @Bean
    UserFpdCcpaMask userFpdCcpaMask(IpAddressHelper ipAddressHelper) {
        return new UserFpdCcpaMask(ipAddressHelper);
    }

    @Bean
    UserFpdCoppaMask userFpdCoppaMask(IpAddressHelper ipAddressHelper) {
        return new UserFpdCoppaMask(ipAddressHelper);
    }

    @Bean
    UserFpdTcfMask userFpdTcfMask(IpAddressHelper ipAddressHelper) {
        return new UserFpdTcfMask(ipAddressHelper);
    }

    @Bean
    ActivityEnforcement activityEnforcement(UserFpdActivityMask userFpdActivityMask) {
        return new ActivityEnforcement(userFpdActivityMask);
    }

    @Bean
    CcpaEnforcement ccpaEnforcement(UserFpdCcpaMask userFpdCcpaMask,
                                    BidderCatalog bidderCatalog,
                                    Metrics metrics,
                                    @Value("${ccpa.enforce}") boolean ccpaEnforce) {

        return new CcpaEnforcement(userFpdCcpaMask, bidderCatalog, metrics, ccpaEnforce);
    }

    @Bean
    CoppaEnforcement coppaEnforcement(UserFpdCoppaMask userFpdCoppaMask, Metrics metrics) {
        return new CoppaEnforcement(userFpdCoppaMask, metrics);
    }

    @Bean
    TcfEnforcement tcfEnforcement(TcfDefinerService tcfDefinerService,
                                  UserFpdTcfMask userFpdTcfMask,
                                  BidderCatalog bidderCatalog,
                                  Metrics metrics,
                                  @Value("${lmt.enforce}") boolean lmtEnforce) {

        return new TcfEnforcement(tcfDefinerService, userFpdTcfMask, bidderCatalog, metrics, lmtEnforce);
    }

    @Data
    @Validated
    public static class VendorListServiceConfigurationProperties {

        @NotEmpty
        String cacheDir;

        @NotEmpty
        String httpEndpointTemplate;

        @Min(1)
        int refreshMissingListPeriodMs;

        String fallbackVendorListPath;

        @NotNull
        Boolean deprecated;

        RetryPolicyConfigurationProperties retryPolicy;
    }
}
