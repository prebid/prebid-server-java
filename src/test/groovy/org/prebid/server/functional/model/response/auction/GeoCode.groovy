package org.prebid.server.functional.model.response.auction

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class GeoCode {

   String country
   String region
}
