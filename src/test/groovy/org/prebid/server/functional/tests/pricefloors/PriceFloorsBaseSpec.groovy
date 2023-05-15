package org.prebid.server.functional.tests.pricefloors

import org.prebid.server.functional.model.Currency
import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountPriceFloorsConfig
import org.prebid.server.functional.model.config.PriceFloorsFetch
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.mock.services.currencyconversion.CurrencyConversionRatesResponse
import org.prebid.server.functional.model.pricefloors.Country
import org.prebid.server.functional.model.pricefloors.MediaType
import org.prebid.server.functional.model.pricefloors.Rule
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.BidRequestExt
import org.prebid.server.functional.model.request.auction.DistributionChannel
import org.prebid.server.functional.model.request.auction.ExtPrebidFloors
import org.prebid.server.functional.model.request.auction.Prebid
import org.prebid.server.functional.model.request.auction.Video
import org.prebid.server.functional.model.response.currencyrates.CurrencyRatesResponse
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.scaffolding.CurrencyConversion
import org.prebid.server.functional.testcontainers.scaffolding.FloorsProvider
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils

import java.math.RoundingMode

import static org.prebid.server.functional.model.Currency.EUR
import static org.prebid.server.functional.model.Currency.GBP
import static org.prebid.server.functional.model.Currency.USD
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.model.request.auction.FetchStatus.INPROGRESS
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

abstract class PriceFloorsBaseSpec extends BaseSpec {

    public static final BigDecimal FLOOR_MIN = 0.5
    public static final BigDecimal FLOOR_MAX = 2
    public static final Map<String, String> floorsConfig = ["price-floors.enabled"           : "true",
                                                            "settings.default-account-config": encode(defaultAccountConfigSettings)]

    protected static final String basicFetchUrl = networkServiceContainer.rootUri + FloorsProvider.FLOORS_ENDPOINT
    protected static final FloorsProvider floorsProvider = new FloorsProvider(networkServiceContainer)
    protected static final CurrencyConversion currencyConversion = new CurrencyConversion(networkServiceContainer)
    protected static final int MAX_MODEL_WEIGHT = 100

    private static final int DEFAULT_MODEL_WEIGHT = 1
    private static final int CURRENCY_CONVERSION_PRECISION = 3
    private static final int FLOOR_VALUE_PRECISION = 4
    private static final Map<String, String> CURRENCY_CONVERTER_CONFIG = ["auction.ad-server-currency"                          : USD as String,
                                                                          "currency-converter.external-rates.enabled"           : "true",
                                                                          "currency-converter.external-rates.url"               : "$networkServiceContainer.rootUri/currency".toString(),
                                                                          "currency-converter.external-rates.default-timeout-ms": "4000",
                                                                          "currency-converter.external-rates.refresh-period-ms" : "900000"]

    protected final PrebidServerService floorsPbsService = pbsServiceFactory.getService(floorsConfig + CURRENCY_CONVERTER_CONFIG)

    def setupSpec() {
        currencyConversion.setCurrencyConversionRatesResponse(CurrencyConversionRatesResponse.getDefaultCurrencyConversionRatesResponse().tap {
            conversions = [(USD): [(EUR): 0.9124920156948626,
                                   (GBP): 0.793776804452961],
                           (GBP): [(USD): 1.2597999770088517,
                                   (EUR): 1.1495574203931487],
                           (EUR): [(USD): 1.3429368029739777]]
        })
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

    protected static Account getAccountWithEnabledFetch(String accountId) {
        def priceFloors = new AccountPriceFloorsConfig(enabled: true,
                fetch: new PriceFloorsFetch(url: basicFetchUrl + accountId, enabled: true))
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
                : new BidRequest(ext: new BidRequestExt(prebid: new Prebid(debug: 1, floors: ExtPrebidFloors.extPrebidFloors)))

    }

    static String getRule() {
        new Rule(mediaType: MediaType.MULTIPLE, country: Country.MULTIPLE).rule
    }

    static int getModelWeight() {
        PBSUtils.getRandomNumber(DEFAULT_MODEL_WEIGHT, MAX_MODEL_WEIGHT)
    }

    static BigDecimal getAdjustedValue(BigDecimal floorValue, BigDecimal bidAdjustment) {
        def adjustedValue = floorValue / bidAdjustment
        PBSUtils.roundDecimal(adjustedValue, FLOOR_VALUE_PRECISION)
    }

    static BidRequest getBidRequestWithMultipleMediaTypes() {
        BidRequest.defaultBidRequest.tap { imp[0].video = Video.defaultVideo }
    }

    protected void cacheFloorsProviderRules(PrebidServerService pbsService = floorsPbsService,
                                            BidRequest bidRequest,
                                            BigDecimal expectedFloorValue) {
        PBSUtils.waitUntil({ pbsService.sendAuctionRequest(bidRequest).ext.debug.resolvedRequest.imp[0].bidFloor == expectedFloorValue },
                5000,
                1000)
    }

    protected void cacheFloorsProviderRules(PrebidServerService pbsService = floorsPbsService, BidRequest bidRequest) {
        PBSUtils.waitUntil({ pbsService.sendAuctionRequest(bidRequest).ext?.debug?.resolvedRequest?.ext?.prebid?.floors?.fetchStatus != INPROGRESS },
                5000,
                1000)
    }

    protected void cacheFloorsProviderRules(PrebidServerService pbsService = floorsPbsService,
                                            AmpRequest ampRequest,
                                            BigDecimal expectedFloorValue) {
        PBSUtils.waitUntil({ pbsService.sendAmpRequest(ampRequest).ext.debug.resolvedRequest.imp[0].bidFloor == expectedFloorValue },
                5000,
                1000)
    }

    protected BigDecimal getRoundedFloorValue(BigDecimal floorValue) {
        floorValue.setScale(FLOOR_VALUE_PRECISION, RoundingMode.HALF_EVEN)
    }

    protected BigDecimal getPriceAfterCurrencyConversion(BigDecimal value,
                                                         Currency currencyFrom, Currency currencyTo,
                                                         CurrencyRatesResponse currencyRatesResponse) {
        def currencyRate = currencyRatesResponse.rates[currencyFrom.value][currencyTo.value]
        def convertedValue = value * currencyRate
        convertedValue.setScale(CURRENCY_CONVERSION_PRECISION, RoundingMode.HALF_EVEN)
    }
}
