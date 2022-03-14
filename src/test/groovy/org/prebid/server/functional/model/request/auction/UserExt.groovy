package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.util.privacy.ConsentString

@ToString(includeNames = true, ignoreNulls = true)
class UserExt {

    String consent
    List<String> fcapids
    UserTime time
    UserExtData data
}
