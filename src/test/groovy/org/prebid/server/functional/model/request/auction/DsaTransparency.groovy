package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class DsaTransparency {

    String domain
    List<DsaTransparencyParam> dsaParams

    static DsaTransparency getDefaultDsaTransparency() {
        new DsaTransparency(domain: PBSUtils.randomString)
    }
}
