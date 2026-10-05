package org.prebid.server.functional.model.request.auction

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.request.amp.AmpRequest

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class Amp {

    AmpRequest data
}
