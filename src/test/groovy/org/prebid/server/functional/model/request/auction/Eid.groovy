package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
class Eid {

    String source
    List<Uid> uids

    static Eid getDefaultEid() {
        new Eid().tap {
            source = PBSUtils.randomString
            uids = [Uid.defaultUid]
        }
    }
}
