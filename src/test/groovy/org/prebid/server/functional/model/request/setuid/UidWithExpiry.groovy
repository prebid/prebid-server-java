package org.prebid.server.functional.model.request.setuid

import groovy.transform.ToString

import java.time.Clock
import java.time.ZonedDateTime

@ToString(includeNames = true, ignoreNulls = true)
class UidWithExpiry {

    String uid
    ZonedDateTime expires

    static UidWithExpiry getDefaultUidWithExpiry(Integer daysUntilExpiry = 2) {
        new UidWithExpiry().tap {
            uid = UUID.randomUUID().toString()
            expires = ZonedDateTime.now(Clock.systemUTC()).plusDays(daysUntilExpiry)
        }
    }
}
