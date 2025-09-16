package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class HookId {

    String moduleCode
    String hookImplCode

    @JsonProperty("module_code")
    String moduleCodeSnakeCase
    @JsonProperty("hook_impl_code")
    String hookImplCodeSnakeCase
}
