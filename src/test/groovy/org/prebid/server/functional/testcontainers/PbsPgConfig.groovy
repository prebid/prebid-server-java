package org.prebid.server.functional.testcontainers

import org.prebid.server.functional.model.Currency
import org.prebid.server.functional.testcontainers.container.NetworkServiceContainer
import org.prebid.server.functional.util.PBSUtils

import java.time.LocalDate

import static org.prebid.server.functional.testcontainers.scaffolding.pg.Alert.ALERT_ENDPOINT_PATH
import static org.prebid.server.functional.testcontainers.scaffolding.pg.DeliveryStatistics.REPORT_DELIVERY_ENDPOINT_PATH
import static org.prebid.server.functional.testcontainers.scaffolding.pg.GeneralPlanner.PLANS_ENDPOINT_PATH
import static org.prebid.server.functional.testcontainers.scaffolding.pg.GeneralPlanner.REGISTER_ENDPOINT_PATH
import static org.prebid.server.functional.testcontainers.scaffolding.pg.UserData.USER_DETAILS_ENDPOINT_PATH
import static org.prebid.server.functional.testcontainers.scaffolding.pg.UserData.WIN_EVENT_ENDPOINT_PATH

class PbsPgConfig {

    public static final String PG_ENDPOINT_USERNAME = "pg"
    public static final String PG_ENDPOINT_PASSWORD = "pg"

    private static final int NEXT_MONTH = LocalDate.now().plusMonths(1).monthValue

    final Map<String, String> properties
    final String env
    final String dataCenter
    final String region
    final String system
    final String subSystem
    final String hostId
    final String vendor
    final Currency currency
    final String userIdType
    final int maxDealsPerBidder
    final int lineItemsPerReport

    PbsPgConfig(NetworkServiceContainer networkServiceContainer) {
        properties = getPgConfig(networkServiceContainer.rootUri).asImmutable()
        env = properties.get("profile")
        dataCenter = properties.get("data-center")
        region = properties.get("datacenter-region")
        system = properties.get("system")
        subSystem = properties.get("sub-system")
        hostId = properties.get("host-id")
        vendor = properties.get("vendor")
        currency = properties.get("auction.ad-server-currency")
        userIdType = properties.get("deals.user-data.user-ids[0].type")
        maxDealsPerBidder = getIntProperty(properties, "deals.max-deals-per-bidder")
        lineItemsPerReport = getIntProperty(properties, "deals.delivery-stats.line-items-per-report")
    }

    private static Map<String, String> getPgConfig(String networkServiceContainerUri) {
        pbsGeneralSettings() + adminDealsUpdateEndpoint() + deals() + deliveryProgress() +
                planner(networkServiceContainerUri) + deliveryStatistics(networkServiceContainerUri) +
                alert(networkServiceContainerUri) + userData(networkServiceContainerUri)
    }

    private static Map<String, String> pbsGeneralSettings() {
        ["host-id"                   : PBSUtils.randomString,
         "datacenter-region"         : PBSUtils.randomString,
         "vendor"                    : PBSUtils.randomString,
         "profile"                   : PBSUtils.randomString,
         "system"                    : PBSUtils.randomString,
         "sub-system"                : PBSUtils.randomString,
         "data-center"               : PBSUtils.randomString,
         "auction.ad-server-currency": "USD",
        ]
    }

    private static Map<String, String> adminDealsUpdateEndpoint() {
        ["admin-endpoints.force-deals-update.enabled": "true"]
    }

    private static Map<String, String> deals() {
        ["deals.enabled"             : "true",
         "deals.simulation.enabled"  : "false",
         "deals.max-deals-per-bidder": "3"
        ]
    }

    private static Map<String, String> planner(String networkServiceContainerUri) {
        ["deals.planner.plan-endpoint"      : networkServiceContainerUri + PLANS_ENDPOINT_PATH,
         "deals.planner.register-endpoint"  : networkServiceContainerUri + REGISTER_ENDPOINT_PATH,
         "deals.planner.update-period"      : "0 15 10 15 $NEXT_MONTH ?" as String,
         "deals.planner.plan-advance-period": "0 15 10 15 $NEXT_MONTH ?" as String,
         "deals.planner.timeout-ms"         : "5000",
         "deals.planner.username"           : PG_ENDPOINT_USERNAME,
         "deals.planner.password"           : PG_ENDPOINT_PASSWORD,
         "deals.planner.register-period-sec": "3600"
        ]
    }

    private static Map<String, String> deliveryStatistics(String networkServiceContainerUri) {
        ["deals.delivery-stats.endpoint"                   : networkServiceContainerUri +
                REPORT_DELIVERY_ENDPOINT_PATH,
         "deals.delivery-stats.username"                   : PG_ENDPOINT_USERNAME,
         "deals.delivery-stats.password"                   : PG_ENDPOINT_PASSWORD,
         "deals.delivery-stats.delivery-period"            : "0 15 10 15 $NEXT_MONTH ?" as String,
         "deals.delivery-stats.timeout-ms"                 : "10000",
         "deals.delivery-stats.request-compression-enabled": "false",
         "deals.delivery-stats.line-items-per-report"      : "5"
        ]
    }

    private static Map<String, String> deliveryProgress() {
        ["deals.delivery-progress.report-reset-period": "0 15 10 15 $NEXT_MONTH ?" as String]
    }

    private static Map<String, String> alert(String networkServiceContainerUri) {
        ["deals.alert-proxy.enabled"    : "true",
         "deals.alert-proxy.url"        : networkServiceContainerUri + ALERT_ENDPOINT_PATH,
         "deals.alert-proxy.username"   : PG_ENDPOINT_USERNAME,
         "deals.alert-proxy.password"   : PG_ENDPOINT_PASSWORD,
         "deals.alert-proxy.timeout-sec": "10"
        ]
    }

    private static Map<String, String> userData(String networkServiceContainerUri) {
        ["deals.user-data.user-details-endpoint": networkServiceContainerUri + USER_DETAILS_ENDPOINT_PATH,
         "deals.user-data.win-event-endpoint"   : networkServiceContainerUri + WIN_EVENT_ENDPOINT_PATH,
         "deals.user-data.timeout"              : "1000",
         "deals.user-data.user-ids[0].type"     : "autotest",
         "deals.user-data.user-ids[0].source"   : "uid",
         "deals.user-data.user-ids[0].location" : "generic"
        ]
    }

    private static getIntProperty(Map<String, String> properties, String propertyName) {
        def property = properties.get(propertyName)
        property ? property as int : -1
    }
}
