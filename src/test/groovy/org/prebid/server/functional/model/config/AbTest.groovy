package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class AbTest {

    Boolean enabled
    String moduleCode
    @JsonProperty("module_code")
    String moduleCodeSnakeCase
    Set<String> accounts
    Integer percentActive
    @JsonProperty("percent_active")
    Integer percentActiveSnakeCase
    Boolean logAnalyticsTag
    @JsonProperty("log_analytics_tag")
    Boolean logAnalyticsTagSnakeCase

    static AbTest getDefault(String moduleCode, List<String> accounts = null) {
        new AbTest(enabled: true,
                moduleCode: moduleCode,
                accounts: accounts,
                percentActive: 0,
                logAnalyticsTag: true)
    }
}
