package org.prebid.server.functional.model.request.auction

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = false)
class SchainNode {

    String asi
    String sid
    Integer hp
    String rid
    String name
    String domain
}
