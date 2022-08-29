package org.prebid.server.functional.model.request.auction

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class SupplyChain {

    Integer complete
    List<SupplyChainNode> nodes
    String ver

    static SupplyChain getDefaultSupplyChain(){
        new SupplyChain().tap {
            complete = PBSUtils.randomNumber
            nodes = [SupplyChainNode.defaultSupplyChainNode]
            ver = PBSUtils.randomString
        }
    }
}
