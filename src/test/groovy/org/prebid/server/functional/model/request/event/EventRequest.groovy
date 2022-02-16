package org.prebid.server.functional.model.request.event

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString
import org.prebid.server.functional.model.request.Format
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.request.Format.IMAGE
import static org.prebid.server.functional.model.request.event.EventType.WIN

@ToString(includeNames = true, ignoreNulls = true)
class EventRequest {

    @JsonProperty("t")
    EventType type
    @JsonProperty("b")
    String bidId
    @JsonProperty("a")
    Integer accountId
    @JsonProperty("f")
    Format format
    String bidder
    @JsonProperty("x")
    Integer analytics
    @JsonProperty("ts")
    Long timestamp
    @JsonProperty("l")
    String lineItemId

    static EventRequest getDefaultEventRequest() {
        def request = new EventRequest()
        request.type = WIN
        request.bidId = PBSUtils.randomString
        request.accountId = PBSUtils.randomNumber
        request.format = IMAGE
        request.bidder = "generic"
        request.timestamp = System.currentTimeMillis()
        request
    }
}
