package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
@ToString(includeNames = true, ignoreNulls = true)
class DsaTransparency {

    String domain
    List<Integer> dsaParams

    static DsaTransparency getDefaultRegsDsaTransparency() {
        new DsaTransparency(domain: PBSUtils.randomString)
    }
}

