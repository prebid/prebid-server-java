package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class Dooh {
    
    String id
    String name
    List<String> venuetype
    Integer venuetypetax
    Publisher publisher
    String domain
    String keywords
    Content content
}
