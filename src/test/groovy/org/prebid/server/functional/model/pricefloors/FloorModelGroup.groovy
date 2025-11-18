package org.prebid.server.functional.model.pricefloors

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.Currency
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.util.PBSUtils

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class FloorModelGroup {

    Currency currency
    Integer skipRate
    String modelVersion
    Integer modelWeight
    PriceFloorSchema schema
    Map<String, BigDecimal> values
    @JsonProperty("default")
    BigDecimal defaultFloor
    List<BidderName> noFloorSignalBidders

    static FloorModelGroup getModelGroup() {
        new FloorModelGroup(
                currency: Currency.USD,
                schema: PriceFloorSchema.priceFloorSchema,
                values: [(new Rule(mediaType: MediaType.MULTIPLE, country: Country.MULTIPLE)
                        .getRule([PriceFloorField.MEDIA_TYPE, PriceFloorField.COUNTRY])): PBSUtils.randomFloorValue]
        )
    }
}
