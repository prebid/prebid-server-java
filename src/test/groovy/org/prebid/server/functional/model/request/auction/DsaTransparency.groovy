package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
class DsaTransparency {

    String domain
    List<Integer> dsaparams

    static DsaTransparency getDefaultRegsDsaTransparency() {
        new DsaTransparency(domain: PBSUtils.randomString)
    }
}

