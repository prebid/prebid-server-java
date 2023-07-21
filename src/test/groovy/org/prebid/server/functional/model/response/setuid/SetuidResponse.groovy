package org.prebid.server.functional.model.response.setuid

import groovy.transform.ToString
import io.restassured.http.Headers
import org.prebid.server.functional.model.UidsCookie

@ToString(includeNames = true, ignoreNulls = true)
class SetuidResponse {

    Headers headers
    UidsCookie uidsCookie
    Byte[] responseBody
}
