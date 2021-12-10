package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString
import org.prebid.server.bidder.grid.model.ExtImpGridData
import org.prebid.server.functional.model.bidder.AppNexus
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.bidder.Rubicon

@ToString(includeNames = true, ignoreNulls = true)
class ImpExt {

    ImpExtPrebid prebid
    ImpExtData data
    Generic generic
    @Deprecated
    Rubicon rubicon
    @Deprecated
    @JsonProperty("appnexus")
    AppNexus appNexus
    ImpExtContext context
    ImpExtContextData data

    static ImpExt getDefaultImpExt() {
        new ImpExt().tap {
            prebid = ImpExtPrebid.defaultImpExtPrebid
        }
    }
}
