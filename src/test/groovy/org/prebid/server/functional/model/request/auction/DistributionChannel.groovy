package org.prebid.server.functional.model.request.auction

enum DistributionChannel {

    SITE, APP, DOOH

    String getValue() {
        name().toLowerCase()
    }

    static DistributionChannel findByValue(String value) {
        values().find { it.getValue().equalsIgnoreCase(value) }
    }
}
