package org.prebid.server.functional.model.request.auction

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class Source {

    Fd fd
    String tid
    String pchain
    SupplyChain schain
    SourceExt ext

    static Source getDefaultSource(){
        new Source().tap {
            schain = SupplyChain.defaultSupplyChain
        }
    }
}
