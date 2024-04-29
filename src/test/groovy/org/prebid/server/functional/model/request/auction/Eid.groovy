package org.prebid.server.functional.model.request.auction

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class Eid {

    String source
    List<Uid> uids

    static Eid getDefaultEid(String source = PBSUtils.randomString) {
        new Eid().tap {
            it.source = source
            it.uids = [Uid.defaultUid]
        }
    }
}
