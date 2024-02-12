package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.DsaTransparency
import org.prebid.server.functional.util.PBSUtils

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class Dsa {

    String behalf
    String paid
    List<DsaTransparency> transparency
    DsaAdRender adRender

    static Dsa getDefaultDsa() {
        new Dsa(
                behalf: PBSUtils.randomString,
                paid: PBSUtils.randomString,
                adRender: PBSUtils.getRandomEnum(DsaAdRender),
                transparency: [DsaTransparency.defaultDsaTransparency]
        )
    }
}
