package org.prebid.server.functional.model.request.cookiesync

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class FilterSettings {

    MethodFilter iframe
    MethodFilter image
}
