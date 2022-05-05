package org.prebid.server.functional.model.request.auction

import groovy.transform.AutoClone
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.pricefloors.Country

@AutoClone
@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class Geo {

    BigDecimal lat
    BigDecimal lon
    Integer type
    Integer accuracy
    Integer lastfix
    Integer ipservice
    Country country
    String region
    String regionfips104
    String metro
    String city
    String zip
    Integer utcoffset
    GeoExt ext
}
