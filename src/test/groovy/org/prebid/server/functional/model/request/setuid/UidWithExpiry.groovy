package org.prebid.server.functional.model.request.setuid

import groovy.transform.ToString

import java.time.ZonedDateTime

@ToString(includeNames = true, ignoreNulls = true)
class UidWithExpiry {

    String uid
    ZonedDateTime expires
}
