package org.prebid.server.functional.model.response.influx

import com.fasterxml.jackson.annotation.JsonProperty

class InfluxResult {

    @JsonProperty("statement_id")
    Integer statementId
    List<Series> series
}
