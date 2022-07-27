package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class UserAgent {
    
    List<BrandVersion> browsers
    BrandVersion platform
    Integer mobile
    String architecture
    String bitness
    String model
    Integer source
}
