package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class UserExt {

    String consent
    List<Eid> eids
    List<String> fcapids
    UserTime time
    UserExtData data
}
