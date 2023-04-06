package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.model.Currency
import org.prebid.server.functional.model.pricefloors.PriceFloorData
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.tests.pricefloors.PriceFloorsBaseSpec.FLOOR_MIN

@ToString(includeNames = true, ignoreNulls = true)
class ExtPrebidFloors {

    Boolean enabled
    Location location
    FetchStatus fetchStatus
    Boolean skipped
    Currency floorMinCur
    BigDecimal floorMin
    String floorProvider
    ExtPrebidPriceFloorEnforcement enforcement
    Integer skipRate
    PriceFloorData data

    static ExtPrebidFloors getExtPrebidFloors() {
        new ExtPrebidFloors(floorMin: FLOOR_MIN,
                floorProvider: PBSUtils.randomString,
                data: PriceFloorData.priceFloorData
        )
    }
}
