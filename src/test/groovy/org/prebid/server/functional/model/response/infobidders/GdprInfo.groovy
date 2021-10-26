package org.prebid.server.functional.model.response.infobidders

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class GdprInfo {

    Integer vendorId
    Boolean enforced
}
