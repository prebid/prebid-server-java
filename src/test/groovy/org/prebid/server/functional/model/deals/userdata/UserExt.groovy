package org.prebid.server.functional.model.deals.userdata

import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
class UserExt {

    List<String> fcapIds

    static getDefaultUserExt() {
        new UserExt(fcapIds: [PBSUtils.randomString])
    }
}
