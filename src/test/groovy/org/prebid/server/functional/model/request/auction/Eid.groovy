package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class Eid {

    String source
    List<Uid> uids
    String inserter
    String matcher
    @JsonProperty("mm")
    Integer matchMethod

    static Eid getDefaultEid(String source = PBSUtils.randomString) {
        new Eid().tap {
            it.source = source
            it.uids = [Uid.defaultUid]
            it.inserter = PBSUtils.randomString
            it.matcher = PBSUtils.randomString
            it.matchMethod = PBSUtils.randomNumber
        }
    }

    static Eid from(EidPermission eidPermission, List<Uid> uids = [Uid.defaultUid]) {
        new Eid().tap {
            it.source = eidPermission.source
            it.uids = uids
            it.inserter = eidPermission.inserter
            it.matcher = eidPermission.matcher
            it.matchMethod = eidPermission.matchMethod
        }
    }
}
