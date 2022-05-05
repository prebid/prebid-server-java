package org.prebid.server.spring.config;

import io.prometheus.client.dropwizard.samplebuilder.CustomMappingSampleBuilder;
import io.prometheus.client.dropwizard.samplebuilder.DefaultSampleBuilder;
import io.prometheus.client.dropwizard.samplebuilder.MapperConfig;
import io.prometheus.client.dropwizard.samplebuilder.SampleBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
public class PrometheusMapperConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "metrics.prometheus.labels", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    public SampleBuilder defaultSampleBuilder() {
        return new DefaultSampleBuilder();
    }

    @Bean
    @ConditionalOnProperty(prefix = "metrics.prometheus.labels", name = "enabled", havingValue = "true")
    public SampleBuilder labelsSampleBuilder(List<List<MapperConfig>> mapperConfigs) {
        final List<MapperConfig> configs = mapperConfigs.stream().flatMap(List::stream).collect(Collectors.toList());

        return new CustomMappingSampleBuilder(configs);
    }

    @Bean
    public List<MapperConfig> basicMappings() {
        // example: vertx.http.servers.[IP]:[PORT].delete-requests
        // Another option would be to match over the different ports and use a reasonable tag name
        // However ":" is not supported in the matching reqex of the mapper config
        MapperConfig vertxServer = new MapperConfig(
                "vertx.http.servers.*.*.*.*.*", "vertx.http.servers.${4}",
                Map.of("address", "${0}.${1}.${2}.${3}"));

        // example: requests.ok.openrtb2-web
        MapperConfig requestsOk = new MapperConfig(
                "requests.*.*",
                "requests.status.type",
                Map.of("status", "${0}", "type", "${1}"));

        // example: bidder-cardinality.4.requests
        MapperConfig bidderCardinality = new MapperConfig(
                "bidder-cardinality.*.requests",
                "bidder-cardinality.requests",
                Map.of("cardinality", "${0}"));

        return List.of(vertxServer, requestsOk, bidderCardinality);
    }

    @Bean
    public List<MapperConfig> vertxRequestConfigs() {
        // example: vertx.http.clients.get-requests
        // example: vertx.http.clients.post-requests
        return Stream.of("get", "post", "head", "delete", "connect", "options", "patch", "put", "other", "trace")
                .map(request -> new MapperConfig(
                                "vertx.http.clients." + request + "-requests",
                                "vertx.http.clients.requests_by_type",
                                Map.of("request", request)
                        )
                ).collect(Collectors.toList());
    }

    @Bean
    public List<MapperConfig> vertxResponseConfig() {
        return Stream.of("1xx", "2xx", "3xx", "4xx")
                .map(response -> new MapperConfig(
                        "vertx.http.clients.responses-" + response,
                        "vertx.http.clients.response_by_status",
                        Map.of("responses", response))
                ).collect(Collectors.toList());
    }

    @Bean
    public List<MapperConfig> privacyMappingConfigs() {
        final MapperConfig privacyTcfMissing = new MapperConfig(
                "privacy.tcf.missing",
                "privacy.tcf.errors",
                Map.of("error", "missing"));

        final MapperConfig privacyTcfInvalid = new MapperConfig(
                "privacy.tcf.invalid",
                "privacy.tcf.errors",
                Map.of("error", "invalid"));

        final MapperConfig privacyVendorList = new MapperConfig(
                "privacy.tcf.*.vendorlist.*",
                "privacy.tcf.vendorlist",
                Map.of("tcf", "${0}", "status", "${1}"));

        final MapperConfig privacyTcfStatus = new MapperConfig(
                "privacy.tcf.*.*",
                "privacy.tcf.${1}",
                Map.of("tcf", "${0}"));

        return List.of(privacyTcfMissing, privacyTcfInvalid, privacyVendorList, privacyTcfStatus);
    }

    @Bean
    public List<MapperConfig> accountModuleMapperConfigs() {
        // example: account.<account>.modules.module_<module>.failure
        //          account.<account>.modules_module_<module>.call
        //          account.<account>.modules_module_<module>.timeout

        final MapperConfig accountModuleCalls = new MapperConfig(
                "account.*.modules.module.*.*",
                "account.module_calls",
                Map.of("account", "${0}", "module", "${1}", "action", "${2}"));

        return List.of(accountModuleCalls);
    }

    @Bean
    public List<MapperConfig> moduleMapperConfigs() {
        // example: modules_module_<module>_stage_<stage>_hook_<hook>_failure

        final MapperConfig moduleCalls = new MapperConfig(
                "modules.module.*.stage.*.hook.*.*",
                "module.calls",
                Map.of("module", "${0}", "stage", "${1}", "hook", "${2}", "action", "${3}"));

        return List.of(moduleCalls);
    }

    @Bean
    public List<MapperConfig> accountMapperConfigs() {
        // example: account.<account>.adapter.pubmatic.request_time
        final MapperConfig accountRequestTime = new MapperConfig(
                "account.*.adapter.*.request_time",
                "account.request_time",
                Map.of("account", "${0}", "adapter", "${1}"));

        // example: account.<account>.adapter.appnexus.requests.gotbids
        final MapperConfig accountAdapterRequests = new MapperConfig(
                "account.*.adapter.*.requests.*",
                "account.requests.responses",
                Map.of("account", "${0}", "adapter", "${1}", "response", "${2}"));

        // example: account.<account>.adapter.appnexus.bids_received
        final MapperConfig accountAdapterBidsReceived = new MapperConfig(
                "account.*.adapter.*.bids_received",
                "account.bids_received",
                Map.of("account", "${0}", "adapter", "${1}"));

        // example: account.<account>.requests.type.openrtb2-web
        final MapperConfig accountRequestTypeConfig = new MapperConfig(
                "account.*.requests.type.*",
                "account.requests.type",
                Map.of("account", "${0}", "type", "${1}"));

        // example: account_<account>_adapter_pubmatic_prices
        final MapperConfig accountPricesConfig = new MapperConfig(
                "account.*.adapter.*.prices",
                "account.prices",
                Map.of("account", "${0}", "adapter", "${1}"));

        // example: adapter.yieldlab.response.validation.size.warn
        final MapperConfig accountValidationConfig = new MapperConfig(
                "account.*.response.validation.*.*",
                "account.response.validation",
                Map.of("account", "${0}", "validation", "${1}", "level", "${2}"));

        // example: account.<account>.requests
        final MapperConfig accountCacheConfig = new MapperConfig(
                "account.*.prebid_cache.requests.*",
                "account.requests.type",
                Map.of("account", "${0}", "result", "${1}"));

        // example: account.<account>.requests
        final MapperConfig accountRequestsConfig = new MapperConfig(
                "account.*.requests",
                "account.requests.type",
                Map.of("account", "${0}"));

        return List.of(accountAdapterRequests, accountAdapterBidsReceived, accountRequestTypeConfig,
                accountRequestTime, accountPricesConfig, accountValidationConfig, accountCacheConfig,
                accountRequestsConfig);
    }

    @Bean
    public List<MapperConfig> adapterMapperConfigs() {
        // example: adapter.yieldlab.request_time
        final MapperConfig adapterRequestTime = new MapperConfig(
                "adapter.*.request_time",
                "adapter.request_time",
                Map.of("adapter", "${0}"));

        // example: adapter.ix.requests.type.openrtb2-web
        final MapperConfig adapterRequestsType = new MapperConfig(
                "adapter.*.requests.type.*",
                "adapter.requests.type",
                Map.of("adapter", "${0}", "type", "${1}"));

        // example: adapter.yieldlab.requests.nobid
        // example: adapter.yieldlab.requests.timeout
        // example: adapter.yieldlab.requests.unknown_error
        // example: adapter.yieldlab.requests.gotbids
        final MapperConfig adapterRequestsResult = new MapperConfig(
                "adapter.*.requests.*",
                "adapter.requests.result",
                Map.of("adapter", "${0}", "result", "${1}"));

        // example: adapter.pubmatic.bids_received
        final MapperConfig adapterBidsReceived = new MapperConfig(
                "adapter.*.bids_received",
                "adapter.bids_received",
                Map.of("adapter", "${0}"));

        // example: adapter.pubmatic.price
        final MapperConfig adapterPrices = new MapperConfig(
                "adapter.*.prices",
                "adapter.prices",
                Map.of("adapter", "${0}"));

        // example: adapter.yieldlab.response.validation.size.warn
        final MapperConfig adapterValidation = new MapperConfig(
                "adapter.*.response.validation.*.*",
                "adapter.response.validation",
                Map.of("adapter", "${0}", "validation", "${1}", "level", "${2}"));

        // example: adapter.yieldlab.no_cookie_requests
        final MapperConfig adapterNoCookieConfig = new MapperConfig(
                "adapter.*.no_cookie_requests",
                "adapter.no_cookie_requests",
                Map.of("adapter", "${0}"));

        // example: adapter.ix.openrtb2-web.tcf.request_blocked
        final MapperConfig adapterRequestTypeTcfConfig = new MapperConfig(
                "adapter.*.*.tcf.*",
                "adapter.tcf",
                Map.of("adapter", "${0}", "type", "${1}", "tcf", "${2}"));

        // example: adapter.appnexus.banner.adm_bids_received
        // example: adapter.appnexus.xNative.adm_bids_received
        final MapperConfig adapterMediaType = new MapperConfig(
                "adapter.*.*.*",
                "adapter.media_type",
                Map.of("adapter", "${0}", "mediaType", "${1}", "metric", "${2}"));

        // example: cookie_sync.rubicon.tcf.blocked
        final MapperConfig adapterCookieSyncTcfConfig = new MapperConfig(
                "cookie_sync.*.tcf.*",
                "adapter.cookie_sync.tcf",
                Map.of("adapter", "${0}", "tcf", "${1}"));

        // example: cookie_sync.appnexus.gen
        // example: cookie_sync.ix.matches
        final MapperConfig adapterGenCookieConfig = new MapperConfig(
                "cookie_sync.*.*",
                "adapter.cookie_sync.action",
                Map.of("adapter", "${0}", "action", "${1}"));

        // example: usersync.triplelift.sets
        final MapperConfig adapterUserSyncSets = new MapperConfig(
                "usersync.*.*",
                "adapter.usersync.action",
                Map.of("adapter", "${0}", "action", "${1}"));

        return List.of(adapterRequestsType, adapterRequestsResult, adapterPrices,
                adapterBidsReceived, adapterValidation, adapterNoCookieConfig,
                adapterUserSyncSets, adapterCookieSyncTcfConfig, adapterGenCookieConfig,
                adapterRequestTypeTcfConfig, adapterRequestTime, adapterMediaType);
    }
}
