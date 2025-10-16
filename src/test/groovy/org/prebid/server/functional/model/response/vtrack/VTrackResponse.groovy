package org.prebid.server.functional.model.response.vtrack

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class VTrackResponse {

    Integer statusCode
    TransferValue responseBody
}
