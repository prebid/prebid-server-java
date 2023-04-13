package org.prebid.server.functional.tests.privacy.activity

import org.prebid.server.functional.model.bidder.AppNexus
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.bidder.Openx
import org.prebid.server.functional.model.bidder.Rubicon
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountPrivacyConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.activitie.AllowActivities
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.DistributionChannel
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.PbsPgConfig
import org.prebid.server.functional.tests.BaseSpec

import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.bidder.BidderName.RUBICON
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

abstract class ActivityBaseSpec extends BaseSpec {

    private static final String IS_ENABLED = 'true'
    private static final String ACTION_URL = "$networkServiceContainer.rootUri/auction"
    private static final Map<String, String> GENERIC_CONFIG = [
            "adapters.${GENERIC.value}.endpoint": ACTION_URL,
            "adapters.${GENERIC.value}.enabled" : IS_ENABLED]
    private static final Map<String, String> OPENX_CONFIG = [
            "adapters.${OPENX.value}.endpoint": ACTION_URL,
            "adapters.${OPENX.value}.enabled" : IS_ENABLED]
    private static final Map<String, String> PBS_CONFIG = GENERIC_CONFIG + OPENX_CONFIG
    private static final PbsPgConfig pgConfig = new PbsPgConfig(networkServiceContainer)

    protected PrebidServerService pbsServerService = pbsServiceFactory.getService(PBS_CONFIG + pgConfig.properties)

    protected static BidRequest getBidRequestWithAccount(DistributionChannel channel = SITE,
                                                         String accountId,
                                                         BidderName bidderName = GENERIC) {

        BidRequest.getDefaultBidRequest(channel).tap {
            (channel == SITE ? site.publisher : app.publisher).id = accountId
            imp.first().ext.prebid.bidder.generic = null
            switch (bidderName) {
                case OPENX:
                    return imp.first().ext.prebid.bidder.openx = Openx.defaultOpenx
                case ALIAS:
                    return imp.first().ext.prebid.bidder.alias = new Generic()
                case APPNEXUS:
                    return imp.first().ext.prebid.bidder.appNexus = AppNexus.default
                case RUBICON:
                    return imp.first().ext.prebid.bidder.rubicon = Rubicon.defaultRubicon
                default:
                    return imp.first().ext.prebid.bidder.generic = new Generic()
            }
        }
    }


    protected static Account getDefaultAccount(String accountId, AllowActivities activities) {
        def privacy = new AccountPrivacyConfig(activities: activities)
        def accountConfig = new AccountConfig(privacy: privacy)
        new Account(uuid: accountId, config: accountConfig)
    }
}
