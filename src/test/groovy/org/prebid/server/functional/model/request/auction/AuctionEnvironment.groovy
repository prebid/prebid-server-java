package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue
import org.prebid.server.functional.util.PBSUtils

enum AuctionEnvironment {

    NOT_SUPPORTED(0),
    DEVICE_ORCHESTRATED(1),
    SERVER_ORCHESTRATED(3),
    UNKNOWN(Integer.MAX_VALUE),

    @JsonValue
    private int value

    AuctionEnvironment(Integer value) {
        this.value = value
    }
}
