package org.prebid.server.functional.model.response.cookiesync

import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName

@ToString(includeNames = true, ignoreNulls = true)
class BidderUsersyncStatus {

    BidderName bidder
    String error
    Boolean no_cookie
    UsersyncInfo usersync
}
