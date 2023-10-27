package org.prebid.server.functional.model.request.auction

enum DistributionChannel {

    SITE, APP, DOOH

    String getValue() {
        name().toLowerCase()
    }
}
