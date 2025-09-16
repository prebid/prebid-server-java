package org.prebid.server.functional.model.request.auction

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class Publisher {

    String id
    String name
    Integer cattax
    List<String> cat
    String domain

    static Publisher getDefaultPublisher() {
        new Publisher().tap {
            id = PBSUtils.randomNumber.toString()
        }
    }

    static Publisher getFPDPublisher() {
        new Publisher().tap {
            id = PBSUtils.randomNumber.toString()
            name = PBSUtils.randomString
            domain = PBSUtils.randomString
        }
    }
}
