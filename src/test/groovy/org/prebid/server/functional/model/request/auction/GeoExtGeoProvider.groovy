package org.prebid.server.functional.model.request.auction

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class GeoExtGeoProvider {

    String country
    Object region
    Object metro
    String city
    String zip
}
