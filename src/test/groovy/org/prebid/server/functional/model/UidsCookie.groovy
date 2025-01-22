package org.prebid.server.functional.model

import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.request.setuid.UidWithExpiry

import java.time.Clock
import java.time.ZonedDateTime

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC

@ToString(includeNames = true, ignoreNulls = true)
class UidsCookie {

    Map<BidderName, String> uids
    Map<BidderName, UidWithExpiry> tempUIDs
    Boolean optout

    static UidsCookie getDefaultUidsCookie(BidderName bidder = GENERIC, Integer expireDays = 2) {
        new UidsCookie().tap {
            uids = [(bidder): UUID.randomUUID().toString()]
            tempUIDs = [(bidder): new UidWithExpiry(uid: UUID.randomUUID().toString(),
                    expires: ZonedDateTime.now(Clock.systemUTC()).plusDays(expireDays))]
        }
    }
}
