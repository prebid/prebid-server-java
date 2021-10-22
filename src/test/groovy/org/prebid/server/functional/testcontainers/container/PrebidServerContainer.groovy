package org.prebid.server.functional.testcontainers.container

import org.prebid.server.functional.testcontainers.Dependencies
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.wait.strategy.Wait

class PrebidServerContainer extends GenericContainer<PrebidServerContainer> {

    public static final int PORT = 8080
    public static final int DEBUG_PORT = 8000
    public static final int ADMIN_PORT = 8060
    public static final String ADMIN_ENDPOINT_USERNAME = "user"
    public static final String ADMIN_ENDPOINT_PASSWORD = "user"

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

    private static final Map<String, String> DEFAULT_ENV = [
            "auction.ad-server-currency"                 : "USD",
            "auction.stored-requests-timeout-ms"         : "1000",
            "metrics.prefix"                             : "prebid",
            "status-response"                            : "ok",
            "gdpr.default-value"                         : "0",
            "settings.database.account-query"            : DB_ACCOUNT_QUERY,
            "settings.database.stored-requests-query"    : "SELECT accountId, reqid, requestData, 'request' as dataType FROM stored_requests WHERE reqid IN (%REQUEST_ID_LIST%) UNION ALL SELECT accountId, reqid, requestData, 'imp' as dataType FROM stored_requests WHERE reqid IN (%IMP_ID_LIST%)",
            "settings.database.amp-stored-requests-query": "SELECT accountId, reqid, requestData, 'request' as dataType FROM stored_requests WHERE reqid IN (%REQUEST_ID_LIST%)",
            "settings.database.stored-responses-query"   : "SELECT resid, responseData FROM stored_responses WHERE resid IN (%RESPONSE_ID_LIST%)"
    ]

    PrebidServerContainer(Map<String, String> config) {
        this("prebid/prebid-server:latest", config)
    }

    PrebidServerContainer(String dockerImage, Map<String, String> config) {
        super(dockerImage)
        withExposedPorts(PORT, DEBUG_PORT, ADMIN_PORT)
        waitingFor(Wait.forHttp("/status")
                       .forPort(PORT)
                       .forStatusCode(200))
        withConfig(DEFAULT_ENV)
        withAdminEndpoints()
        withDebug()
        withMysql(Dependencies.mysqlContainer)
        withBidder(Dependencies.networkServiceContainer)
        withDefaultBiddersConfig()
        withPrebidCache(Dependencies.networkServiceContainer)
        withMetricsEndpoint()
        withNetwork(Dependencies.network)
        withConfig(config)
    }

    PrebidServerContainer withMysql(MySQLContainer mysql) {
        withMysql(mysql.getNetworkAliases().get(0),
                mysql.exposedPorts.get(0),
                mysql.databaseName,
                mysql.username,
                mysql.password)
        return self()
    }

    PrebidServerContainer withDebug() {
        withEnv("JAVA_TOOL_OPTIONS", "-agentlib:jdwp=transport=dt_socket,address=$DEBUG_PORT,server=y,suspend=n")
    }

    void withMysql(String host, int port, String dbname, String user, String password) {
        withConfig(["settings.database.type"     : "mysql",
                    "settings.database.host"     : host,
                    "settings.database.port"     : port as String,
                    "settings.database.dbname"   : dbname,
                    "settings.database.user"     : user,
                    "settings.database.password" : password,
                    "settings.database.pool-size": "2" // setting 2 here to leave some slack for the PBS
        ])
    }

    PrebidServerContainer withAdminEndpoints() {
        withConfig(["admin-endpoints.currency-rates.enabled"                           : "true",
                    "currency-converter.external-rates.enabled"                        : "true",
                    ("admin-endpoints.credentials.$ADMIN_ENDPOINT_USERNAME".toString()): ADMIN_ENDPOINT_PASSWORD,
                    "admin-endpoints.logging-httpinteraction.enabled"                  : "true"
        ])
        return self()
    }

    PrebidServerContainer withBidder(NetworkServiceContainer networkServiceContainer) {
        withBidder("$networkServiceContainer.rootUri")
        return self()
    }

    PrebidServerContainer withPrebidCache(NetworkServiceContainer networkServiceContainer) {
        withConfig(["cache.scheme": "http",
                    "cache.host"  : "$networkServiceContainer.hostAndPort".toString(),
                    "cache.path"  : "/cache",
                    "cache.query" : "uuid="
        ])
        return self()
    }

    void withBidder(String host) {
        withConfig(["adapters.generic.enabled"      : "true",
                    "adapters.generic.endpoint"     : "$host/auction".toString(),
                    "adapters.generic.usersync.url" : "$host/generic-usersync".toString(),
                    "adapters.generic.usersync.type": "redirect"
        ])
    }

    void withDefaultBiddersConfig() {
        withConfig(["adapter-defaults.enabled"                   : "false",
                    "adapter-defaults.modifying-vast-xml-allowed": "true",
                    "adapter-defaults.pbs-enforces-ccpa"         : "true"
        ])
    }

    void withMetricsEndpoint() {
        withConfig(["admin-endpoints.collected-metrics.enabled": "true"])
    }

    PrebidServerContainer withConfig(Map<String, String> config) {
        withEnv(normalizeProperties(config))
    }

    int getPort() {
        getMappedPort(PORT)
    }

    int getAdminPort() {
        getMappedPort(ADMIN_PORT)
    }

    String getRootUri() {
        return "http://$host:$port"
    }

    String getAdminRootUri() {
        return "http://$host:$adminPort"
    }

    private static Map<String, String> normalizeProperties(Map<String, String> properties) {
        properties.collectEntries { [normalizeProperty(it.key), it.value] } as Map<String, String>
    }

    private static String normalizeProperty(String property) {
        property.replace(".", "_")
                .replace("-", "")
    }

    @Override
    void close() {
        super.close()
    }
}
