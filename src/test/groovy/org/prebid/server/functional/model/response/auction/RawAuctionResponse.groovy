package org.prebid.server.functional.model.response.auction

import groovy.transform.ToString
import org.prebid.server.functional.model.ResponseModel

@ToString(includeNames = true, ignoreNulls = true)
class RawAuctionResponse implements ResponseModel {

    String responseBody
    Map<String, String> headers
}
