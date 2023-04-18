package org.prebid.server.functional.tests.privacy.activity

import org.prebid.server.functional.model.bidder.AppNexus
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.bidder.Openx
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountPrivacyConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.Consent
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.DistributionChannel
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.PbsPgConfig
import org.prebid.server.functional.tests.BaseSpec

import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

abstract class ActivityBaseSpec extends BaseSpec {

    private static final String IS_ENABLED = 'true'
    private static final String ACTION_URL = "$networkServiceContainer.rootUri/auction"
    private static final Map<String, String> OPENX_CONFIG = ["adapters.${OPENX.value}.endpoint": ACTION_URL,
                                                             "adapters.${OPENX.value}.enabled" : IS_ENABLED]
    private static final PbsPgConfig pgConfig = new PbsPgConfig(networkServiceContainer)
    private static final Map<String, String> PBS_CONFIG = OPENX_CONFIG + pgConfig.properties
    protected PrebidServerService pbsServerService = pbsServiceFactory.getService(PBS_CONFIG)

    protected static BidRequest getBidRequestWithAccount(DistributionChannel channel = SITE,
                                                         String accountId,
                                                         BidderName bidderName = GENERIC) {

        if (![GENERIC, OPENX, APPNEXUS].contains(bidderName)) {
            throw new IllegalStateException("Bidder should be GENERIC, OPENX or APPNEXUS")
        }

        BidRequest request = BidRequest.getDefaultBidRequest(channel)
        request.setAccountId(accountId)
        request.imp.first().ext.prebid.bidder.generic = null
        request.imp.first().ext.prebid.bidder."${bidderName?.value}" = [OPENX   : Openx.defaultOpenx,
                                                                        APPNEXUS: AppNexus.default,
                                                                        GENERIC : new Generic()]["$bidderName"]
        return request
    }

    protected static Account getAccountWithAllowActivities(String accountId, AllowActivities activities) {
        def consent = new Consent(allowActivities: activities)
        def privacy = new AccountPrivacyConfig(consent: consent)
        def accountConfig = new AccountConfig(privacy: privacy)
        new Account(uuid: accountId, config: accountConfig)
    }
}
