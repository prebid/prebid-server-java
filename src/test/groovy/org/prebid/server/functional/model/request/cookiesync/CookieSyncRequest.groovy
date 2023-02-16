package org.prebid.server.functional.model.request.cookiesync

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
class CookieSyncRequest {

    List<BidderName> bidders
    Integer gdpr
    String gdprConsent
    String usPrivacy
    String gpp
    String gppSid
    @JsonProperty("coopSync")
    Boolean coopSync
    Boolean debug
    Integer limit
    String account
    @JsonProperty("filterSettings")
    FilterSettings filterSettings

    static CookieSyncRequest getDefaultCookieSyncRequest() {
        new CookieSyncRequest().tap {
            bidders = [GENERIC]
            gdpr = 0
            coopSync = false
            debug = true
        }
    }
}
