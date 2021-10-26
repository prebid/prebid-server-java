package org.prebid.server.functional.model.response.setuid

import groovy.transform.ToString
import io.restassured.http.Cookie

@ToString(includeNames = true, ignoreNulls = true)
class SetuidResponse {

    Cookie uidsCookie
    String responseBody
}
