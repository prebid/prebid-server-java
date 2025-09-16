package org.prebid.server.functional.model.bidderspecific

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.Imp

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class BidderImp extends Imp {

    BidderImpExt ext
}
