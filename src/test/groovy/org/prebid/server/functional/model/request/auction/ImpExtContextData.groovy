package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class ImpExtContextData {

    String language
    List<String> keywords
    Integer buyerid
    List<Integer> buyerids
}
