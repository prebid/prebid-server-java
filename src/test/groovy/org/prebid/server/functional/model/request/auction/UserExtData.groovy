package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class UserExtData {

    List<String> keywords
    String buyeruid
    List<String> buyeruids
}
