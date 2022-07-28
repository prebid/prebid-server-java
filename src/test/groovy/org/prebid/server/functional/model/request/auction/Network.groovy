package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
class Network {

    String id
    String name
    String domain

    static Network getDefaultNetwork() {
        new Network().tap {
            id = PBSUtils.randomString
        }
    }
}
