package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
class Producer {

    String id
    String name
    Integer cattax
    List<String> cat
    String domain

    static Producer getDefaultProducer(){
        new Producer().tap {
            id = PBSUtils.randomString
        }
    }
}
