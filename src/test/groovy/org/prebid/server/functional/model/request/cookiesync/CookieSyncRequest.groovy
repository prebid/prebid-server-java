package org.prebid.server.functional.model.request.cookiesync

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy)
class CookieSyncRequest {

    List<BidderName> bidders
    Integer gdpr
    String gdprConsent
    String usPrivacy
    @JsonProperty("coopSync")
    Boolean coopSync
    Integer limit
    String account

    static CookieSyncRequest getDefaultCookieSyncRequest() {
        def request = new CookieSyncRequest()
        request.bidders = [GENERIC]
        request.gdpr = 0
        request
    }
}
