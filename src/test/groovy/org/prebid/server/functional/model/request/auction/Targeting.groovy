package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode(excludes = "priceGranularity")
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
class Targeting {

    PriceGranularity priceGranularity
    Boolean includeWinners
    Boolean includeBidderKeys
    Boolean preferDeals
    Boolean alwaysIncludeDeals
    Boolean includeFormat
    String prefix

    static Targeting createWithAllValuesSetTo(Boolean value) {
        new Targeting().tap {
            // Leave true for populating targeting
            includeWinners = true
            includeBidderKeys = value
            preferDeals = value
            alwaysIncludeDeals = value
            includeFormat = value
        }
    }

    static Targeting createWithRandomValues() {
        new Random().with {
            new Targeting().tap {
                // Leave true for populating targeting
                includeWinners = true
                includeBidderKeys = nextBoolean()
                preferDeals = nextBoolean()
                alwaysIncludeDeals = nextBoolean()
                includeFormat = nextBoolean()
            }
        }
    }
}
