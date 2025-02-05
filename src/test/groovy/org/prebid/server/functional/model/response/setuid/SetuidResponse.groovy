package org.prebid.server.functional.model.response.setuid

import groovy.transform.ToString
import org.prebid.server.functional.model.UidsCookie

@ToString(includeNames = true, ignoreNulls = true)
class SetuidResponse {

    Map<String, List<String>> headers
    UidsCookie uidsCookie
    Byte[] responseBody
}
