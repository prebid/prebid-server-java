package org.prebid.server.functional.model.request.auction

import groovy.transform.AutoClone
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

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
    String country
    String region
    String regionfips104
    String metro
    String city
    String zip
    Integer utcoffset
}
