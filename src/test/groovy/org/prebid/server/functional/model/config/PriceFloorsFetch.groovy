package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class PriceFloorsFetch {

    Boolean enabled
    String url
    Long timeoutMs
    @JsonProperty("timeout_ms")
    Long timeoutMsSnakeCase
    Long maxFileSizeKb
    @JsonProperty("max_file_size_kb")
    Long maxFileSizeKbSnakeCase
    Integer maxRules
    @JsonProperty("max_rules")
    Integer maxRulesSnakeCase
    Integer maxAgeSec
    @JsonProperty("max_age_sec")
    Integer maxAgeSecSnakeCase
    Integer periodSec
    @JsonProperty("period_sec")
    Integer periodSecSnakeCase
    Integer maxSchemaDims
    @JsonProperty("max_schema_dims")
    Integer maxSchemaDimsSnakeCase
}
