package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class AssetVideo {

    List<String> mimes
    Integer minduration
    Integer maxduration
    List<Integer> protocols
}
