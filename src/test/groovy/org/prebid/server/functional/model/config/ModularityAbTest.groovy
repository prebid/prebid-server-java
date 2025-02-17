package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class ModularityAbTest {

    Boolean enabled
    String moduleCode
    @JsonProperty("module_code")
    String moduleCodeSnakeCase
    Set<Integer> accounts
    Integer percentActive
    @JsonProperty("percent_active")
    Integer percentActiveSnakeCase
    Boolean logAnalyticsTag
    @JsonProperty("log_analytics_tag")
    Boolean logAnalyticsTagSnakeCase

    static ModularityAbTest getDefault(String moduleCode, List<Integer> accounts = null) {
        new ModularityAbTest(enabled: true,
                moduleCode: moduleCode,
                accounts: accounts,
                percentActive: 0,
                logAnalyticsTag: true)
    }
}
