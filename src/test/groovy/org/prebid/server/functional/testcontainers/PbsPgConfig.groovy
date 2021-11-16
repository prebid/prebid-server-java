package org.prebid.server.functional.testcontainers

import org.prebid.server.functional.testcontainers.container.NetworkServiceContainer

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

    private static final Integer NEXT_MONTH = LocalDate.now().plusMonths(1).monthValue

    static Map<String, String> getPgConfig(NetworkServiceContainer networkServiceContainer) {
        adminDealsUpdateEndpoint() + deals() + planner(networkServiceContainer) +
                deliveryStatistics(networkServiceContainer) + deliveryProgress() + alert(networkServiceContainer) +
                userData(networkServiceContainer)
    }

    static Map<String, String> adminDealsUpdateEndpoint() {
        ["admin-endpoints.force-deals-update.enabled": "true"]
    }

    static Map<String, String> deals() {
        ["deals.enabled"             : "true",
         "deals.simulation.enabled"  : "false",
         "deals.max-deals-per-bidder": "3"
        ]
    }

    static Map<String, String> planner(NetworkServiceContainer networkServiceContainer) {
        String rootUri = networkServiceContainer.rootUri
        ["deals.planner.plan-endpoint"      : rootUri + PLANS_ENDPOINT_PATH,
         "deals.planner.register-endpoint"  : rootUri + REGISTER_ENDPOINT_PATH,
         "deals.planner.update-period"      : "0 15 10 15 $NEXT_MONTH ?" as String,
         "deals.planner.plan-advance-period": "0 15 10 15 $NEXT_MONTH ?" as String,
         "deals.planner.timeout-ms"         : "5000",
         "deals.planner.username"           : PG_ENDPOINT_USERNAME,
         "deals.planner.password"           : PG_ENDPOINT_PASSWORD,
         "deals.planner.register-period-sec": "3600"
        ]
    }

    static Map<String, String> deliveryStatistics(NetworkServiceContainer networkServiceContainer) {
        ["deals.delivery-stats.endpoint"                   : networkServiceContainer.rootUri +
                REPORT_DELIVERY_ENDPOINT_PATH,
         "deals.delivery-stats.username"                   : PG_ENDPOINT_USERNAME,
         "deals.delivery-stats.password"                   : PG_ENDPOINT_PASSWORD,
         "deals.delivery-stats.delivery-period"            : "0 15 10 15 $NEXT_MONTH ?" as String,
         "deals.delivery-stats.timeout-ms"                 : "10000",
         "deals.delivery-stats.request-compression-enabled": "false",
         "deals.delivery-stats.line-items-per-report"      : "5"
        ]
    }

    static Map<String, String> deliveryProgress() {
        ["deals.delivery-progress.report-reset-period": "0 15 10 15 $NEXT_MONTH ?" as String]
    }

    static Map<String, String> alert(NetworkServiceContainer networkServiceContainer) {
        ["deals.alert-proxy.enabled"    : "true",
         "deals.alert-proxy.url"        : networkServiceContainer.rootUri + ALERT_ENDPOINT_PATH,
         "deals.alert-proxy.username"   : PG_ENDPOINT_USERNAME,
         "deals.alert-proxy.password"   : PG_ENDPOINT_PASSWORD,
         "deals.alert-proxy.timeout-sec": "10"
        ]
    }

    static Map<String, String> userData(NetworkServiceContainer networkServiceContainer) {
        String rootUri = networkServiceContainer.rootUri
        ["deals.user-data.user-details-endpoint": rootUri + USER_DETAILS_ENDPOINT_PATH,
         "deals.user-data.win-event-endpoint"   : rootUri + WIN_EVENT_ENDPOINT_PATH,
         "deals.user-data.timeout"              : "1000",
         "deals.user-data.user-ids[0].type"     : "autotest",
         "deals.user-data.user-ids[0].source"   : "uid",
         "deals.user-data.user-ids[0].location" : "generic"
        ]
    }
}
