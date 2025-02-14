package org.prebid.server.functional.model.response.cookiesync

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class RawCookieSyncResponse {

    String responseBody
    Map<String, List<String>> headers
}
