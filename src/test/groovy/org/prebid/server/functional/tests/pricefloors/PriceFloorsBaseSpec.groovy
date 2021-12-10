package org.prebid.server.functional.tests.pricefloors

import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountPriceFloorsConfig
import org.prebid.server.functional.model.config.PriceFloorsFetch
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.pricefloors.Country
import org.prebid.server.functional.model.pricefloors.MediaType
import org.prebid.server.functional.model.pricefloors.Rule
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.ExtPrebidFloors
import org.prebid.server.functional.model.request.auction.Video
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.Dependencies
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.testcontainers.scaffolding.FloorsProvider
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils

@PBSTest
abstract class PriceFloorsBaseSpec extends BaseSpec {

    public static final Map<String, String> floorsConfig = ["price-floors.enabled"           : "true",
                                                               "settings.default-account-config": mapper.encode(defaultAccountConfigSettings)]
    protected static final PrebidServerService floorsPbsService = pbsServiceFactory.getService(floorsConfig)

    protected static final String fetchUrl = Dependencies.networkServiceContainer.rootUri +
            FloorsProvider.FLOORS_ENDPOINT
    protected static final FloorsProvider floorsProvider = new FloorsProvider(Dependencies.networkServiceContainer, Dependencies.objectMapperWrapper)

    protected static final int DEFAULT_MODEL_WEIGHT = 1
    protected static final int MAX_MODEL_WEIGHT = 1000000

    def setupSpec() {
        floorsProvider.setResponse()
    }

    protected static AccountConfig getDefaultAccountConfigSettings() {
        def fetch = new PriceFloorsFetch(enabled: false,
                timeoutMs: 5000,
                maxRules: 0,
                maxFileSizeKb: 200,
                maxAgeSec: 86400,
                periodSec: 3600)
        def floors = new AccountPriceFloorsConfig(enabled: true,
                fetch: fetch,
                enforceFloorsRate: 100,
                enforceDealFloors: true,
                adjustForBidAdjustment: true,
                useDynamicData: true)
        new AccountConfig(auction: new AccountAuctionConfig(priceFloors: floors))
    }

    static Account getAccountWithEnabledFetch(String accountId) {
        def priceFloors = new AccountPriceFloorsConfig(enabled: true, fetch: new PriceFloorsFetch(url: fetchUrl, enabled: true))
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(priceFloors: priceFloors))
        new Account(uuid: accountId, config: accountConfig)
    }

    static BidRequest getBidRequestWithFloors() {
        def floors = ExtPrebidFloors.extPrebidFloors
        BidRequest.defaultBidRequest.tap {
            imp[0].bidFloor = floors.data.modelGroups[0].values[rule]
            imp[0].bidFloorCur = floors.data.modelGroups[0].currency
            ext.prebid.floors = floors
        }
    }

    static String getRule() {
        new Rule(mediaType: MediaType.MULTIPLE, country: Country.MULTIPLE).rule
    }

    static int getModelWeight() {
        PBSUtils.getRandomNumber(DEFAULT_MODEL_WEIGHT, MAX_MODEL_WEIGHT)
    }

    static BigDecimal getRandomFloorValue() {
        PBSUtils.getRoundedFractionalNumber(PBSUtils.getFractionalRandomNumber(1, 2), 2)
    }

    static BigDecimal getRandomCurrencyRate() {
        PBSUtils.getRoundedFractionalNumber(PBSUtils.getFractionalRandomNumber(1, 2), 5)
    }

    static BidRequest getBidRequestWithMultipleMediaTypes(){
        BidRequest.defaultBidRequest.tap { imp[0].video = Video.defaultVideo }
    }
}
