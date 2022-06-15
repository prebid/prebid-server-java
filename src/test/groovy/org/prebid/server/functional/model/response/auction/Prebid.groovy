package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.databind.JsonNode

class Prebid {

    MediaType type
    Map<String, String> targeting
    String targetbiddercode
    Cache cache
    Map passThrough
}
