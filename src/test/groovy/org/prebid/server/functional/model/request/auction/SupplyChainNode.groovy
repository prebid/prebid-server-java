package org.prebid.server.functional.model.request.auction

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class SupplyChainNode {

    String asi
    String sid
    String rid
    String name
    String domain
    Integer hp

    static SupplyChainNode getDefaultSupplyChainNode() {
        new SupplyChainNode().tap {
            asi = PBSUtils.randomString
            sid = PBSUtils.randomString
            rid = PBSUtils.randomString
            name = PBSUtils.randomString
            domain = PBSUtils.randomString
            hp = PBSUtils.randomNumber
        }
    }
}
