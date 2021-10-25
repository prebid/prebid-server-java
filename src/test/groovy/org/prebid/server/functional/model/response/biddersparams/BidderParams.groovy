package org.prebid.server.functional.model.response.biddersparams

import com.fasterxml.jackson.annotation.JsonProperty

class BidderParams {

    @JsonProperty("\$schema")
    String schema
    String title
    String description
    String type
    def properties
    List<OneOf> oneOf
    List<String> required
    def not
    def anyOf
    def appid
    def placementid
    def dependencies
}
