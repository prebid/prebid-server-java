package org.prebid.server.functional.model.request.activitie

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class Component {

    @JsonProperty("in")
    List<String> xIn
    @JsonProperty("notin")
    List<String> notIn

    static Component getBaseComponent(BidderName bidder = GENERIC) {
        new Component(xIn: [bidder.value], notIn: null)
    }
}
