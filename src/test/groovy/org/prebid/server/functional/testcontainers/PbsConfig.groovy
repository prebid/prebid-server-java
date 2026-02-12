package org.prebid.server.functional.testcontainers


import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.PostgreSQLContainer

import static org.prebid.server.functional.testcontainers.Dependencies.networkServiceContainer
import static org.prebid.server.functional.testcontainers.container.PrebidServerContainer.ADMIN_ENDPOINT_PASSWORD
import static org.prebid.server.functional.testcontainers.container.PrebidServerContainer.ADMIN_ENDPOINT_USERNAME
import static org.prebid.server.functional.util.CurrencyUtil.DEFAULT_CURRENCY

final class PbsConfig {

    private static final String DB_ACCOUNT_QUERY = """
SELECT JSON_MERGE_PATCH(JSON_OBJECT('id', uuid,
                                    'status', status,
                                    'auction', JSON_OBJECT('price-granularity', price_granularity,
                                                           'banner-cache-ttl', banner_cache_ttl,
                                                           'video-cache-ttl', video_cache_ttl,
                                                           'truncate-target-attr', truncate_target_attr,
                                                           'default-integration', default_integration,
                                                           'bid-validations', bid_validations,
                                                           'events', JSON_OBJECT('enabled', NOT NOT (events_enabled))),
                                    'privacy', JSON_OBJECT('gdpr', tcf_config),
                                    'analytics', analytics_config),
                        COALESCE(config, '{}')) as consolidated_config
FROM accounts_account
WHERE uuid = %ACCOUNT_ID%
LIMIT 1
"""

    static final Map<String, String> DEFAULT_ENV = [
            "logging.sampling-rate"                      : "1.0",
            "auction.ad-server-currency"                 : DEFAULT_CURRENCY.value,
            "auction.stored-requests-timeout-ms"         : "1000",
            "metrics.prefix"                             : "prebid",
            "status-response"                            : "ok",
            "gdpr.default-value"                         : "0",
            "settings.database.account-query"            : DB_ACCOUNT_QUERY,
            "settings.database.stored-requests-query"    : "SELECT accountId, reqId, requestData, 'request' as dataType FROM stored_requests WHERE reqId IN (%REQUEST_ID_LIST%) UNION ALL SELECT accountId, impId, impData, 'imp' as dataType FROM stored_imps WHERE impId IN (%IMP_ID_LIST%)",
            "settings.database.amp-stored-requests-query": "SELECT accountId, reqId, requestData, 'request' as dataType FROM stored_requests WHERE reqId IN (%REQUEST_ID_LIST%)",
            "settings.database.stored-responses-query"   : "SELECT resId, COALESCE(storedAuctionResponse, storedBidResponse) as responseData FROM stored_responses WHERE resId IN (%RESPONSE_ID_LIST%)"
    ].asImmutable()

    static Map<String, String> getPubstackAnalyticsConfig(String scopeId) {
        ["analytics.pubstack.enabled"                       : "true",
         "analytics.pubstack.endpoint"                      : networkServiceContainer.rootUri,
         "analytics.pubstack.scopeid"                       : scopeId,
         "analytics.pubstack.configuration-refresh-delay-ms": "1000",
         "analytics.pubstack.buffers.size-bytes"            : "1",
         "analytics.pubstack.timeout-ms"                    : "100"].asImmutable()
    }

    static Map<String, String> getHttpSettingsConfig(String rootUri = networkServiceContainer.rootUri) {
        ["settings.http.endpoint"         : "$rootUri/stored-requests".toString(),
         "settings.http.amp-endpoint"     : "$rootUri/amp-stored-requests".toString(),
         "settings.http.video-endpoint"   : "$rootUri/video-stored-requests".toString(),
         "settings.http.category-endpoint": "$rootUri/video-categories".toString()].asImmutable()
    }

    static Map<String, String> getAdminEndpointConfig() {
        ["admin-endpoints.currency-rates.enabled"                           : "true",
         "currency-converter.external-rates.enabled"                        : "true",
         ("admin-endpoints.credentials.$ADMIN_ENDPOINT_USERNAME".toString()): ADMIN_ENDPOINT_PASSWORD,
         "admin-endpoints.logging-httpinteraction.enabled"                  : "true"
        ].asImmutable()
    }

    static Map<String, String> getDefaultBiddersConfig() {
        ["adapter-defaults.enabled"                   : "false",
         "adapter-defaults.ortb-version"              : "2.6",
         "adapter-defaults.modifying-vast-xml-allowed": "true",
         "adapter-defaults.pbs-enforces-ccpa"         : "true"
        ].asImmutable()
    }

    static Map<String, String> getBidderConfig(String rootUri = networkServiceContainer.rootUri) {
        ["adapters.generic.enabled"                    : "true",
         "adapters.generic.endpoint"                   : "$rootUri/auction".toString(),
         "adapters.generic.usersync.cookie-family-name": "generic",
         "adapters.generic.ortb-version"               : "2.6"
        ]
    }

    static Map<String, String> getPrebidCacheConfig(String host = networkServiceContainer.hostAndPort) {
        ["cache.scheme": "http",
         "cache.host"  : "$host".toString(),
         "cache.path"  : "/cache",
         "cache.query" : "uuid="
        ].asImmutable()
    }

    static Map<String, String> getMySqlConfig(MySQLContainer mysql = Dependencies.mysqlContainer) {
        ["settings.database.type"                   : "mysql",
         "settings.database.host"                   : mysql.getNetworkAliases().get(0),
         "settings.database.port"                   : mysql.exposedPorts.get(0) as String,
         "settings.database.dbname"                 : mysql.databaseName,
         "settings.database.user"                   : mysql.username,
         "settings.database.password"               : mysql.password,
         "settings.database.pool-size"              : "2", // setting 2 here to leave some slack for the PBS
         "settings.database.idle-connection-timeout": "300"
        ].asImmutable()
    }
    static Map<String, String> getPostgreSqlConfig(PostgreSQLContainer postgres = Dependencies.postgresqlContainer) {
        ["settings.database.type"                   : "postgres",
         "settings.database.host"                   : postgres.getNetworkAliases().get(0),
         "settings.database.port"                   : postgres.exposedPorts.get(0) as String,
         "settings.database.dbname"                 : postgres.databaseName,
         "settings.database.user"                   : postgres.username,
         "settings.database.password"               : postgres.password,
         "settings.database.pool-size"              : "2", // setting 2 here to leave some slack for the PBS
         "settings.database.idle-connection-timeout": "300"
        ].asImmutable()
    }

    static Map<String, String> getMetricConfig() {
        ["admin-endpoints.collected-metrics.enabled": "true"].asImmutable()
    }

    // due to a config validation we'll need to circumvent all future aliases this way
    static Map<String, String> getBidderAliasConfig() {
        ["adapters.generic.aliases.cwire.meta-info.site-media-types"          : "",
         "adapters.generic.aliases.cwire.meta-info.app-media-types"           : "",
         "adapters.generic.aliases.blue.meta-info.app-media-types"            : "",
         "adapters.generic.aliases.blue.meta-info.site-media-types"           : "",
         "adapters.generic.aliases.nativo.meta-info.app-media-types"          : "",
         "adapters.generic.aliases.nativo.meta-info.site-media-types"         : "",
         "adapters.generic.aliases.infytv.meta-info.app-media-types"          : "",
         "adapters.generic.aliases.infytv.meta-info.site-media-types"         : "",
         "adapters.generic.aliases.zeta-global-ssp.meta-info.app-media-types" : "",
         "adapters.generic.aliases.zeta-global-ssp.meta-info.site-media-types": "",
         "adapters.generic.aliases.ccx.meta-info.app-media-types"             : "",
         "adapters.generic.aliases.ccx.meta-info.site-media-types"            : "",
         "adapters.generic.aliases.adrino.meta-info.app-media-types"          : "",
         "adapters.generic.aliases.adrino.meta-info.site-media-types"         : ""]
    }

    static Map<String, String> getCurrencyConverterConfig() {
        ["auction.ad-server-currency"                          : DEFAULT_CURRENCY.value,
         "currency-converter.external-rates.enabled"           : "true",
         "currency-converter.external-rates.url"               : "$networkServiceContainer.rootUri/currency".toString(),
         "currency-converter.external-rates.default-timeout-ms": "4000",
         "currency-converter.external-rates.refresh-period-ms" : "900000"]
    }

    static Map<String, String> getTargetingConfig() {
        ["settings.targeting.truncate-attr-chars": '255']
    }

    private PbsConfig() {}
}
