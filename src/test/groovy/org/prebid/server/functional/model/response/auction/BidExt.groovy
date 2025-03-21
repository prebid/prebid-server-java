package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString
import org.prebid.server.functional.model.Currency
import org.prebid.server.functional.model.bidder.BidderName

@ToString(includeNames = true, ignoreNulls = true)
class BidExt {

    Prebid prebid
    BigDecimal origbidcpm
    Currency origbidcur
    DsaResponse dsa
    @JsonProperty("ct")
    Integer creativeType
    @JsonProperty("startdelay")
    Integer startDelay
    @JsonProperty("ds")
    String demandSource
    @JsonProperty("bc")
    BidderName bidderCode
}
