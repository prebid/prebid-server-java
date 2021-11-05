package org.prebid.server.functional.model.response

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class BidderError {

    Integer code
    String message
}
