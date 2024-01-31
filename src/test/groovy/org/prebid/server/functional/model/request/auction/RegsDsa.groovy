package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
@ToString(includeNames = true, ignoreNulls = true)
class RegsDsa {

    Integer required
    Integer pubRender
    Integer dataToPub
    List<DsaTransparency> transparency

    static RegsDsa getDefaultRegsDsa(ReqsDsaRequiredType required) {
        new RegsDsa(
                required: required.value,
                pubRender: PBSUtils.getRandomNumber(0, 2),
                dataToPub: PBSUtils.getRandomNumber(0, 2),
                transparency: [DsaTransparency.defaultRegsDsaTransparency]
        )
    }
}
