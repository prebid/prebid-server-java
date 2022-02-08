package org.prebid.server.functional.tests.pg

import org.prebid.server.functional.testcontainers.container.PrebidServerContainer

import static org.prebid.server.functional.testcontainers.container.PrebidServerContainer.normalizeProperty

class PgProperties {

    String env
    String dataCenter
    String region
    String system
    String subSystem
    String hostId
    String vendor
    String currency
    String userIdType
    int maxDealsPerBidder
    int lineItemsPerReport

    PgProperties(PrebidServerContainer prebidServerContainer) {
        Map<String, String> properties = prebidServerContainer.envMap

        env = getStringProperty(properties, "profile")
        dataCenter = getStringProperty(properties, "data-center")
        region = getStringProperty(properties, "datacenter-region")
        system = getStringProperty(properties, "system")
        subSystem = getStringProperty(properties, "sub-system")
        hostId = getStringProperty(properties, "host-id")
        vendor = getStringProperty(properties, "vendor")
        currency = getStringProperty(properties, "auction.ad-server-currency")
        userIdType = getStringProperty(properties, "deals.user-data.user-ids[0].type")
        maxDealsPerBidder = getIntProperty(properties, "deals.max-deals-per-bidder")
        lineItemsPerReport = getIntProperty(properties, "deals.delivery-stats.line-items-per-report")
    }

    private static getStringProperty(Map<String, String> properties, String propertyName) {
        properties.get(normalizeProperty(propertyName))
    }

    private static getIntProperty(Map<String, String> properties, String propertyName) {
        def property = properties.get(normalizeProperty(propertyName))
        property ? property as int : -1
    }
}
