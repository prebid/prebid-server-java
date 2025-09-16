package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString
enum Endpoint {

    OPENRTB2_AUCTION("/openrtb2/auction"),
    OPENRTB2_AMP("/openrtb2/amp"),
    OPENRTB2_VIDEO("/openrtb2/video"),
    COOKIE_SYNC("/cookie_sync"),
    SETUID("/setuid"),
    BIDDER_PARAMS("/bidders/params"),
    EVENT("/event"),
    GETUIDS("/getuids"),
    INFO_BIDDERS("/info/bidders"),
    OPTOUT("/optout"),
    STATUS("/status"),
    VTRACK("/vtrack")

    @JsonValue
    final String value

    Endpoint(String value) {
        this.value = value
    }

    @Override
    String toString() {
        value
    }
}
