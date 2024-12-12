package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue
import org.prebid.server.functional.util.PBSUtils

enum AuctionEnvironment {

    NOT_SUPPORTED(0),
    DEVICE_ORCHESTRATED(1),
    SERVER_ORCHESTRATED(3),
    UNKNOWN(PBSUtils.getRandomNumberWithExclusion([NOT_SUPPORTED.value,
                                                   DEVICE_ORCHESTRATED.value,
                                                   SERVER_ORCHESTRATED.value])),

    @JsonValue
    private int value

    AuctionEnvironment(Integer value) {
        this.value = value
    }
}
