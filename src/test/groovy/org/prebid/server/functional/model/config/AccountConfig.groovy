package org.prebid.server.functional.model.config

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.AccountStatus

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategy.KebabCaseStrategy.class)
class AccountConfig {

    String id
    AccountStatus status
    AccountAuctionConfig auction
    AccountPrivacyConfig privacy
    AccountAnalyticsConfig analytics
    AccountCookieSyncConfig cookieSync
    AccountHooksConfiguration hooks
}
