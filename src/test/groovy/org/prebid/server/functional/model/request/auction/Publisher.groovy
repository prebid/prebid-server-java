package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
class Publisher {

    String id
    String name
    List<String> cat
    String domain

    static Publisher getDefaultPublisher() {
        new Publisher().tap {
            id = PBSUtils.randomNumber.toString()
        }
    }
}
