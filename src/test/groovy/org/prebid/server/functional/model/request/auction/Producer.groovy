package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class Producer {

    String id
    String name
    List<String> cat
    String domain
}
