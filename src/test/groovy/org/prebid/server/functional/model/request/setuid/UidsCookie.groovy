package org.prebid.server.functional.model.request.setuid

import com.fasterxml.jackson.annotation.JsonFormat
import groovy.transform.ToString

import java.time.Clock
import java.time.ZonedDateTime

@ToString(includeNames = true, ignoreNulls = true)
class UidsCookie {

    Map<String, String> uids
    Map<String, UidWithExpiry> tempUIDs
    Boolean optout
    @JsonFormat(pattern = "yyyy-MM-dd'T'hh:mm:ss.SSS'Z'", timezone = "UTC")
    ZonedDateTime bday

    static UidsCookie getDefaultUidsCookie() {
        def uidsCookie = new UidsCookie()
        uidsCookie.uids = [generic: UUID.randomUUID().toString()]
        uidsCookie.bday = ZonedDateTime.now(Clock.systemUTC())
        uidsCookie.tempUIDs = ["generic": new UidWithExpiry(uid: UUID.randomUUID().toString(), expires: ZonedDateTime.now(Clock.systemUTC()).plusDays(2))]
        uidsCookie
    }
}
