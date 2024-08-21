package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.request.auction.DsaPubRender.PUB_MIGHT_RENDER

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class Dsa {

    DsaRequired dsaRequired
    DsaPubRender pubRender
    DsaDataToPub dataToPub
    List<DsaTransparency> transparency

    static Dsa getDefaultDsa(DsaRequired dsaRequired = PBSUtils.getRandomEnum(DsaRequired)) {
        new Dsa(dsaRequired: dsaRequired,
                pubRender: PUB_MIGHT_RENDER,
                dataToPub: PBSUtils.getRandomEnum(DsaDataToPub),
                transparency: [DsaTransparency.defaultDsaTransparency])
    }
}
