package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class ExtRequestPrebidData {

    List<String> bidders
    List<EidPermission> eidpermissions
}
