package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class Deal {

    String id
    Float bidfloor
    String bidfloorcur
    Integer at
    List<String> wseat
    List<String> wadomain
    DealExt ext
}
