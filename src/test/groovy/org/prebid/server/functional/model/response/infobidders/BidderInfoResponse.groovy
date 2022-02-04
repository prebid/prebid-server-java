package org.prebid.server.functional.model.response.infobidders

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class BidderInfoResponse {

    BidderStatus status
    Boolean usesHttps
    MaintainerInfo maintainer
    Map<String, PlatformInfo> capabilities
    String aliasOf

    enum BidderStatus {

        ACTIVE, DISABLED
    }
}
