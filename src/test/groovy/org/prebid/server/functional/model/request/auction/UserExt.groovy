package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class UserExt {

    String consent
    List<String> fcapids
    UserTime time
    UserExtData data
}
