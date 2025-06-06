package org.prebid.server.functional.tests.pricefloors

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountPriceFloorsConfig
import org.prebid.server.functional.model.config.PriceFloorsFetch
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.pricefloors.Country
import org.prebid.server.functional.model.pricefloors.MediaType
import org.prebid.server.functional.model.pricefloors.Rule
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.BidRequestExt
import org.prebid.server.functional.model.request.auction.DistributionChannel
import org.prebid.server.functional.model.request.auction.ExtPrebidFloors
import org.prebid.server.functional.model.request.auction.FetchStatus
import org.prebid.server.functional.model.request.auction.Prebid
import org.prebid.server.functional.model.request.auction.Video
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.scaffolding.CurrencyConversion
import org.prebid.server.functional.testcontainers.scaffolding.FloorsProvider
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils

import java.math.RoundingMode

import static org.prebid.server.functional.model.request.auction.DebugCondition.ENABLED
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.model.request.auction.FetchStatus.INPROGRESS
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

abstract class PriceFloorsBaseSpec extends BaseSpec {

    public static final BigDecimal FLOOR_MIN = 0.5
    public static final BigDecimal FLOOR_MAX = 2
    public static final Map<String, String> FLOORS_CONFIG = ["price-floors.enabled": "true"]

    protected static final FloorsProvider floorsProvider = new FloorsProvider(networkServiceContainer)

    protected static final String BASIC_FETCH_URL = networkServiceContainer.rootUri + FloorsProvider.FLOORS_ENDPOINT
    protected static final int MAX_MODEL_WEIGHT = 100
    protected static final Closure<String> INVALID_CONFIG_METRIC = { account -> "alerts.account_config.${account}.price-floors" }

    protected static final Closure<String> URL_EMPTY_ERROR = { url -> "Failed to fetch price floor from provider for fetch.url '${url}'"
    }
    protected static final String FETCHING_DISABLED_ERROR = "Fetching is disabled"
    protected static final Closure<String> PRICE_FLOORS_ERROR_LOG = { bidRequest, reason, warningMessage ->
        "Price Floors can't be resolved for account ${bidRequest.accountId} and request ${bidRequest.id}, reason: ${PRICE_FLOORS_WARNING_MESSAGE(reason, warningMessage)}"
    }
    protected static final Closure<String> WARNING_MESSAGE = { message ->
        "Price floors processing failed: parsing of request price floors is failed: $message"
    }
    protected static final Closure<String> FETCHING_FLOORS_ERROR_LOG = { bidRequest, warningMessage ->
        "Price floor fetching failed for account ${bidRequest.accountId}: ${URL_EMPTY_ERROR("$BASIC_FETCH_URL${bidRequest.accountId}")}, with a reason: $warningMessage"
    }
    private static final Closure<String> PRICE_FLOORS_WARNING_MESSAGE = { reason, details ->
        "Price floors processing failed: $reason. Following parsing of request price floors is failed: $details"
    }

    protected static final CurrencyConversion currencyConversion = new CurrencyConversion(networkServiceContainer)

    protected static final int FLOOR_VALUE_PRECISION = 4
    private static final int DEFAULT_MODEL_WEIGHT = 1

    protected final PrebidServerService floorsPbsService = pbsServiceFactory.getService(FLOORS_CONFIG + GENERIC_ALIAS_CONFIG)

    def setupSpec() {
        currencyConversion.setCurrencyConversionRatesResponse()
        floorsProvider.setResponse()
    }

    protected static AccountConfig getDefaultAccountConfigSettings() {
        def fetch = new PriceFloorsFetch(enabled: false,
                timeoutMs: 5000,
                maxRules: 0,
                maxFileSizeKb: 200,
                maxAgeSec: 86400,
                periodSec: 3600,
                maxSchemaDims: 5)
        def floors = new AccountPriceFloorsConfig(enabled: true,
                fetch: fetch,
                enforceFloorsRate: 100,
                enforceDealFloors: true,
                adjustForBidAdjustment: true,
                useDynamicData: true,
                maxRules: 0,
                maxSchemaDims: 3)
        new AccountConfig(auction: new AccountAuctionConfig(priceFloors: floors))
    }

    protected static Account getAccountWithEnabledFetch(String accountId) {
        def priceFloors = new AccountPriceFloorsConfig(enabled: true,
                fetch: new PriceFloorsFetch(url: BASIC_FETCH_URL + accountId, enabled: true))
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(priceFloors: priceFloors))
        new Account(uuid: accountId, config: accountConfig)
    }

    protected static BidRequest getBidRequestWithFloors(DistributionChannel channel = SITE) {
        def floors = ExtPrebidFloors.extPrebidFloors
        BidRequest.getDefaultBidRequest(channel).tap {
            imp[0].bidFloor = floors.data.modelGroups[0].values[rule]
            imp[0].bidFloorCur = floors.data.modelGroups[0].currency
            ext.prebid.floors = floors
        }
    }

    static BidRequest getStoredRequestWithFloors(DistributionChannel channel = SITE) {
        channel == SITE
                ? BidRequest.defaultStoredRequest.tap { ext.prebid.floors = ExtPrebidFloors.extPrebidFloors }
                : new BidRequest(ext: new BidRequestExt(prebid: new Prebid(debug: ENABLED, floors: ExtPrebidFloors.extPrebidFloors)))

    }

    static String getRule() {
        new Rule(mediaType: MediaType.MULTIPLE, country: Country.MULTIPLE).rule
    }

    static int getModelWeight() {
        PBSUtils.getRandomNumber(DEFAULT_MODEL_WEIGHT, MAX_MODEL_WEIGHT)
    }

    static BidRequest getBidRequestWithMultipleMediaTypes() {
        BidRequest.defaultBidRequest.tap { imp[0].video = Video.defaultVideo }
    }

    protected void cacheFloorsProviderRules(BidRequest bidRequest,
                                            BigDecimal expectedFloorValue,
                                            PrebidServerService pbsService = floorsPbsService,
                                            BidderName bidderName = BidderName.GENERIC) {
        PBSUtils.waitUntil({ getRequests(pbsService.sendAuctionRequest(bidRequest))[bidderName.value].first.imp[0].bidFloor == expectedFloorValue },
                5000,
                1000)
    }

    protected void cacheFloorsProviderRules(BidRequest bidRequest,
                                            PrebidServerService pbsService = floorsPbsService,
                                            BidderName bidderName = BidderName.GENERIC,
                                            FetchStatus fetchStatus = INPROGRESS) {
        PBSUtils.waitUntil({ getRequests(pbsService.sendAuctionRequest(bidRequest))[bidderName.value]?.first?.ext?.prebid?.floors?.fetchStatus != fetchStatus },
                5000,
                1000)
    }

    protected void cacheFloorsProviderRules(AmpRequest ampRequest,
                                            BigDecimal expectedFloorValue,
                                            PrebidServerService pbsService = floorsPbsService,
                                            BidderName bidderName = BidderName.GENERIC) {
        PBSUtils.waitUntil({ getRequests(pbsService.sendAmpRequest(ampRequest))[bidderName.value].first.imp[0].bidFloor == expectedFloorValue },
                5000,
                1000)
    }

    protected BigDecimal getRoundedFloorValue(BigDecimal floorValue) {
        floorValue.setScale(FLOOR_VALUE_PRECISION, RoundingMode.HALF_EVEN)
    }
}
