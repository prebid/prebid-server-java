package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.AppNexus
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.bidder.Openx
import org.prebid.server.functional.model.bidder.Rubicon

@ToString(includeNames = true, ignoreNulls = true)
class Bidder {

    Generic alias
    Generic generic
    @JsonProperty("gener_x")
    Generic generX
    @JsonProperty("GeNerIc")
    Generic genericCamelCase
    Rubicon rubicon
    @JsonProperty("appnexus")
    AppNexus appNexus
    Openx openx
    Ix ix

    static Bidder getDefaultBidder() {
        new Bidder().tap {
            generic = new Generic()
        }
    }

    @JsonIgnore
    List<String> getConfiguredBidders() {
        this.class.declaredFields.findAll { !it.synthetic && this[it.name] != null }
            .collect { it.name }
    }
}
