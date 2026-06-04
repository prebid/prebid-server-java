package org.prebid.server.functional.model.bidderspecific

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Imp

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class BidderRequest extends BidRequest {

    private List<BidderImp> imp

    void setImp(List<? extends Imp> imp) {
        this.imp = imp as List<BidderImp>
    }

    List<BidderImp> getImp() {
        return imp
    }
}
