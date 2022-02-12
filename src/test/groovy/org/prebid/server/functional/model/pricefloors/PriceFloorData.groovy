package org.prebid.server.functional.model.pricefloors

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.Currency
import org.prebid.server.functional.util.PBSUtils

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class PriceFloorData {

    String floorProvider
    Currency currency
    Integer skipRate
    String floorsSchemaVersion
    Integer modelTimestamp
    List<ModelGroup> modelGroups

    static PriceFloorData getPriceFloorData() {
        new PriceFloorData(floorProvider: PBSUtils.randomString,
                currency: Currency.USD,
                floorsSchemaVersion: 2,
                modelGroups: [ModelGroup.modelGroup])
    }
}
