package org.prebid.server.functional.model.deals.userdata

import groovy.transform.ToString

import java.time.ZonedDateTime

@ToString(includeNames = true, ignoreNulls = true)
class UserDetailsRequest {

    ZonedDateTime time
    List<UserId> ids
}
