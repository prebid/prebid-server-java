package org.prebid.server.functional.model.bidderspecific

import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.BidRequest

@ToString(includeNames = true, ignoreNulls = true)
class BidderRequest extends BidRequest {

    List<BidderImp> imp
}
