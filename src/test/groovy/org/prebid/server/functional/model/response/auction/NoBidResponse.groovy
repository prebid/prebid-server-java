package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
enum NoBidResponse {

    UNKNOWN_ERROR(0),
    TECHNICAL_ERROR(1),
    INVALID_REQUEST(2),
    KNOWN_WEB_CRAWLER(3),
    SUSPECTED_NON_HUMAN_TRAFFIC(4),
    CLOUD_DATA_CENTER_OR_PROXY_IP(5),
    UNSUPPORTED_DEVICE(6),
    BLOCKED_PUBLISHER_OR_SITE(7),
    UNMATCHED_USER(8),
    DAILY_USER_CAP_MET(9),
    DAILY_DOMAIN_CAP_MET(10),
    ADS_TXT_AUTHORIZATION_UNAVAILABLE(11),
    ADS_TXT_AUTHORIZATION_VIOLATION(12),
    ADS_CART_AUTHORIZATION_UNAVAILABLE(13),
    ADS_CART_AUTHORIZATION_VIOLATION(14),
    INSUFFICIENT_AUCTION_TIME(15),
    INCOMPLETE_SUPPLY_CHAIN(16),
    BLOCKED_SUPPLY_CHAIN(17),
    EXCHANGE_SPECIFIC_VALUES(500)

    @JsonValue
    final Integer nbr

    NoBidResponse(Integer nbr) {
        this.nbr = nbr
    }
}
