package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString
import org.prebid.server.functional.model.response.auction.MediaType

@ToString(includeNames = true, ignoreNulls = true)
class GenericPreferredBidder {

    @JsonProperty("prefmtype")
    MediaType preferredMediaType
}
