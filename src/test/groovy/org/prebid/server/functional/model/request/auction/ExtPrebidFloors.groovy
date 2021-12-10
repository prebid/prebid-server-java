package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.model.pricefloors.PriceFloorData
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
class ExtPrebidFloors {

    Boolean enabled
    Location location
    FetchStatus fetchStatus
    Boolean skipped
    BigDecimal floorMin
    String floorProvider
    ExtPrebidPriceFloorEnforcement enforcement
    Integer skipRate
    PriceFloorData data

    static ExtPrebidFloors getExtPrebidFloors(){
        new ExtPrebidFloors(
                floorMin: 0.5,
                floorProvider: PBSUtils.randomString,
                data: PriceFloorData.priceFloorData
        )
    }
}
