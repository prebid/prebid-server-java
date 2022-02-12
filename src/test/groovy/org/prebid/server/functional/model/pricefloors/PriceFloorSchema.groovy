package org.prebid.server.functional.model.pricefloors

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

import static org.prebid.server.functional.model.pricefloors.PriceFloorField.COUNTRY
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.MEDIA_TYPE

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class PriceFloorSchema {

    String delimiter
    List<PriceFloorField> fields

    static PriceFloorSchema getPriceFloorSchema() {
        new PriceFloorSchema(fields: [MEDIA_TYPE, COUNTRY])
    }
}
