package org.prebid.server.functional.model.pricefloors

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.Currency
import org.prebid.server.functional.util.PBSUtils

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class ModelGroup {

    Currency currency
    Integer skipRate
    String modelVersion
    Integer modelWeight
    PriceFloorSchema schema
    Map<String, BigDecimal> values
    @JsonProperty("default")
    BigDecimal defaultFloor

    static ModelGroup getModelGroup() {
        new ModelGroup(
                currency: Currency.USD,
                schema: PriceFloorSchema.priceFloorSchema,
                values: [(new Rule(mediaType: MediaType.MULTIPLE, country: Country.MULTIPLE)
                        .getRule([PriceFloorField.MEDIA_TYPE, PriceFloorField.COUNTRY])): PBSUtils.randomFloorValue]
        )
    }
}
