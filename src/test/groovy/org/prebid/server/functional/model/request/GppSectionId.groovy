package org.prebid.server.functional.model.request

import org.prebid.server.functional.util.PBSUtils

enum GppSectionId {

    TCF_EU_V2("2"),
    USP_V1("6"),
    UNKNOWN(PBSUtils.randomNumber.toString()),
    INVALID(PBSUtils.getRandomString())

    final String value

    GppSectionId(String value) {
        this.value = value
    }

    Integer getIntValue() {
        value.toInteger()
    }
}
