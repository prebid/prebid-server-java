package org.prebid.server.functional.model

import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.request.setuid.UidWithExpiry

import java.time.Clock
import java.time.ZonedDateTime

@ToString(includeNames = true, ignoreNulls = true)
class UidsCookie {

    Map<String, String> uids
    Map<String, UidWithExpiry> tempUIDs
    Boolean optout
    ZonedDateTime bday

    static UidsCookie getDefaultUidsCookie(BidderName bidderName = BidderName.GENERIC) {
        new UidsCookie().tap {
            uids = Map.of(bidderName.value, UUID.randomUUID().toString())
            bday = ZonedDateTime.now(Clock.systemUTC())
            tempUIDs = Map.of(bidderName.value, UidWithExpiry.defaultUidWithExpiry)
        }
    }
}
