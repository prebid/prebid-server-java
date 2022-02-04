package org.prebid.server.functional.model.mock.services.pubstack

import groovy.transform.ToString
import org.prebid.server.functional.model.ResponseModel

@ToString(includeNames = true, ignoreNulls = true)
class PubStackResponse implements ResponseModel {

    String scopeId
    String endpoint
    Map<EventType, Boolean> features

    static PubStackResponse getDefaultPubStackResponse(String scopeIdValue, String endpointValue) {
        new PubStackResponse().tap {
            scopeId = scopeIdValue
            endpoint = endpointValue
            features = allEventTypeEnabled
        }
    }

    private static Map<EventType, Boolean> getAllEventTypeEnabled() {
        EventType.values().collectEntries { [it, true] }
    }
}
