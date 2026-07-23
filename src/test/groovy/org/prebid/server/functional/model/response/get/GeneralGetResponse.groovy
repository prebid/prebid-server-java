package org.prebid.server.functional.model.response.get

import groovy.transform.ToString
import org.prebid.server.functional.model.response.amp.AmpResponseExt

@ToString(includeNames = true, ignoreNulls = true)
class GeneralGetResponse {

    Map<String, String> targeting
    GeneralGetResponseExt ext
}
