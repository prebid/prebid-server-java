package org.prebid.server.functional.model

import groovy.transform.ToString
import org.prebid.server.functional.model.request.setuid.UidWithExpiry

import java.time.Clock
import java.time.ZonedDateTime

@ToString(includeNames = true, ignoreNulls = true)
class UidsCookie {

    Map<String, String> uids
    Map<String, UidWithExpiry> tempUIDs
    Boolean optout
    ZonedDateTime bday

    static UidsCookie getDefaultUidsCookie() {
        new UidsCookie().tap {
            uids = ["generic": UUID.randomUUID().toString()]
            bday = ZonedDateTime.now(Clock.systemUTC())
            tempUIDs = ["generic": new UidWithExpiry(uid: UUID.randomUUID().toString(), expires: ZonedDateTime.now(Clock.systemUTC()).plusDays(2))]
        }
    }
}
