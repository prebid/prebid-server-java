package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.model.pricefloors.PriceFloorEnforcement

@ToString(includeNames = true, ignoreNulls = true)
class ExtPrebidPriceFloorEnforcement extends PriceFloorEnforcement {

    Integer enforceRate
}
