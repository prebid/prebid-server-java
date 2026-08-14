package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.AppNexus
import org.prebid.server.functional.model.bidder.IxDiag

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class BidRequestExt {

    Prebid prebid
    SupplyChain schain
    @JsonProperty("appnexus")
    AppNexus appNexus
    String bc
    String platform
    @JsonProperty("ixdiag")
    IxDiag ixDiag
}
