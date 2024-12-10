package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.AppNexus
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.bidder.Rubicon

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class ImpExt {

    ImpExtPrebid prebid
    Generic generic
    @Deprecated
    Rubicon rubicon
    @Deprecated
    @JsonProperty("appnexus")
    AppNexus appNexus
    ImpExtContext context
    ImpExtContextData data
    String tid
    String gpid
    String sid
    Integer ae
    String all
    String skadn
    String general
    AnyUnsupportedBidder anyUnsupportedBidder

    static ImpExt getDefaultImpExt() {
        new ImpExt().tap {
            prebid = ImpExtPrebid.defaultImpExtPrebid
        }
    }
}
