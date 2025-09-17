package org.prebid.server.functional.model.request.auction

<<<<<<< HEAD
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.AppNexus

@EqualsAndHashCode
=======
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.AppNexus

>>>>>>> 04d9d4a13 (Initial commit)
@ToString(includeNames = true, ignoreNulls = true)
class BidRequestExt {

    Prebid prebid
    SupplyChain schain
    AppNexus appnexus
    String bc
    String platform
    IxDiag ixdiag
}
