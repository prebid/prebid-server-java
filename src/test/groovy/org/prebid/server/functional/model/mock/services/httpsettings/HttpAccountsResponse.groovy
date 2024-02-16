package org.prebid.server.functional.model.mock.services.httpsettings

import groovy.transform.ToString
import org.prebid.server.functional.model.ResponseModel
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.auction.AccountAuctionConfig
import org.prebid.server.functional.model.config.auction.AccountEventsConfig
import org.prebid.server.functional.model.config.privacy.AccountGdprConfig
import org.prebid.server.functional.model.config.privacy.AccountPrivacyConfig

@ToString(includeNames = true, ignoreNulls = true)
class HttpAccountsResponse implements ResponseModel {

    Map<String, AccountConfig> accounts

    static HttpAccountsResponse getDefaultHttpAccountsResponse(String accountId) {
        def account = new AccountConfig().tap {
            id = accountId
            auction = new AccountAuctionConfig(events: new AccountEventsConfig(enabled: true))
            privacy = new AccountPrivacyConfig(gdpr: new AccountGdprConfig(enabled: false))
        }

        new HttpAccountsResponse(accounts: [(accountId): account])
    }
}
