package org.prebid.server.functional.model.response.amp

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class RawAmpResponse {

    String responseBody
    Map<String, List<String>> headers
}
