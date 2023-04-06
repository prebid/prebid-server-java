package org.prebid.server.functional.model.response.amp

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class AmpResponse {

    Map<String, String> targeting
    AmpResponseExt ext
}
