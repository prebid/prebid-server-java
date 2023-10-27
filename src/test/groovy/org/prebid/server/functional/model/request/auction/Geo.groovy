package org.prebid.server.functional.model.request.auction

import groovy.transform.AutoClone
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.pricefloors.Country
import org.prebid.server.functional.util.PBSUtils

@AutoClone
@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class Geo {

    Float lat
    Float lon
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

    static Geo getFPDGeo() {
        new Geo().tap {
            zip = PBSUtils.randomString
            country = PBSUtils.getRandomEnum(Country)
        }
    }
}
