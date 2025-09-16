package org.prebid.server.functional.model.response.getuids

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class GetuidResponse {

    Map<String, String> buyeruids
}
