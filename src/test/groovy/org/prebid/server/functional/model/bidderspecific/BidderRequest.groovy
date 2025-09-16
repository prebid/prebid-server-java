package org.prebid.server.functional.model.bidderspecific

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.BidRequest

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class BidderRequest extends BidRequest {

    List<BidderImp> imp
}
