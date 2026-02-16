package org.prebid.server.functional.model.request.auction

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.AppNexus

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class BidRequestExt {

    Prebid prebid
    SupplyChain schain
    AppNexus appnexus
    String bc
    String platform
    IxDiag ixdiag
}
