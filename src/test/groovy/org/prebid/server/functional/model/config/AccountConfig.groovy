package org.prebid.server.functional.model.config

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.AccountStatus

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class AccountConfig {

    String id
    AccountStatus status
    AccountAuctionConfig auction
    AccountPrivacyConfig privacy
    AccountAnalyticsConfig analytics
    AccountMetricsConfig metrics
    AccountCookieSyncConfig cookieSync
    AccountHooksConfiguration hooks
    AccountSetting settings

    static getDefaultAccountConfig() {
        new AccountConfig(status: AccountStatus.ACTIVE)
    }
}
