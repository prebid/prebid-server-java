package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString
enum Endpoint {

    AUCTION("/openrtb2/auction"),
    AMP("/openrtb2/amp"),
    VIDEO("/openrtb2/video"),
    COOKIE_SYNC("/cookie_sync"),
    SETUID("/setuid"),
    BIDDER_PARAMS("/bidders/params"),
    EVENT("/event"),
    GETUIDS("/getuids"),
    INFO_BIDDERS("/info/bidders"),
    CURRENCY_RATES("/currency/rates"),
    HTTP_INTERACTION("/logging/httpinteraction"),
    COLLECTED_METRICS("/collected-metrics"),
    PROMETHEUS_METRICS ("/metrics"),
    INFLUX_DB("/query"),
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
