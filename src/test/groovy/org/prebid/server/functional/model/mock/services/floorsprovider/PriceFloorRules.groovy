package org.prebid.server.functional.model.mock.services.floorsprovider

import groovy.transform.ToString
import org.prebid.server.functional.model.ResponseModel
import org.prebid.server.functional.model.pricefloors.PriceFloorData
import org.prebid.server.functional.model.pricefloors.PriceFloorEnforcement
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.tests.pricefloors.PriceFloorsBaseSpec.FLOOR_MIN

@ToString(includeNames = true, ignoreNulls = true)
class PriceFloorRules implements ResponseModel {

    BigDecimal floorMin
    String floorProvider
    PriceFloorEnforcement enforcement
    Integer skipRate
    PriceFloorEndpoint endpoint
    PriceFloorData data

    static PriceFloorRules getPriceFloorRules() {
        new PriceFloorRules(floorMin: FLOOR_MIN,
                floorProvider: PBSUtils.randomString,
                data: PriceFloorData.priceFloorData)
    }
}
