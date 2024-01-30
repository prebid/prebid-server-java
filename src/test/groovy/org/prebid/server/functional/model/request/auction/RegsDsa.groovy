package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import lombok.EqualsAndHashCode
import org.prebid.server.functional.util.PBSUtils

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class RegsDsa {

    Integer required
    Integer pubrender
    Integer datatopub
    List<DsaTransparency> transparency

    static RegsDsa getDefaultRegsDsa(Integer required) {
        new RegsDsa(
                "required": required,
                "pubrender": PBSUtils.getRandomNumber(0, 2),
                "datatopub": PBSUtils.getRandomNumber(0, 2),
                "transparency": [DsaTransparency.defaultRegsDsaTransparency]
        )
    }
}
