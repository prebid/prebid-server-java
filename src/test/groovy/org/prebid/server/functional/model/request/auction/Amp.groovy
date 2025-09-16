package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.model.request.amp.AmpRequest

@ToString(includeNames = true, ignoreNulls = true)
class Amp {

    AmpRequest data
}
