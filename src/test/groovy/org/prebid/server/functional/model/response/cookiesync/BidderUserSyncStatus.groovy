package org.prebid.server.functional.model.response.cookiesync

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName

@ToString(includeNames = true, ignoreNulls = true)
class BidderUserSyncStatus {

    BidderName bidder
    String error
    @JsonProperty("no_cookie")
    Boolean noCookie
    @JsonProperty("usersync")
    UserSyncInfo userSync
}
