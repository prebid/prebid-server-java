package org.prebid.server.functional.model.request.cookiesync

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class MethodFilter<T> {

    // Here we use wildcard for different compatibility
    T bidders
    FilterType filter
}
