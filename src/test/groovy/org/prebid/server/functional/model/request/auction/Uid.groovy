package org.prebid.server.functional.model.request.auction

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class Uid {

    String id
    Integer atype
    UidExt ext

    static Uid getDefaultUid() {
        new Uid().tap {
            id = PBSUtils.randomString
            atype = PBSUtils.randomNumber
        }
    }
}
