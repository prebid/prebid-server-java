package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue

enum DeviceType {

    MOBILE_TABLET_GENERAL(1),
    PERSONAL_COMPUTER(2),
    CONNECTED_TV(3),
    PHONE(4),
    TABLET(5),
    CONNECTED_DEVICE(6),
    SET_TOP_BOX(7),
    OOH_DEVICE(8)

    @JsonValue
    final Integer value

    DeviceType(Integer value) {
        this.value = value
    }
}
