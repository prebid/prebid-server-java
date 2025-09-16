package org.prebid.server.functional.model.response.biddersparams

import com.fasterxml.jackson.annotation.JsonAnySetter

class BiddersParamsResponse {

    @JsonAnySetter
    Map<String, BidderParams> parameters = [:]
}
