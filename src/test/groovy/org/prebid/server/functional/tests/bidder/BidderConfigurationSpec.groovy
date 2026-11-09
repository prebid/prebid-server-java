package org.prebid.server.functional.tests.bidder

import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.config.Endpoint
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.model.request.vtrack.VtrackRequest
import org.prebid.server.functional.model.request.vtrack.xml.Vast
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.CcpaConsent

import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.GENER_X
import static org.prebid.server.functional.model.bidder.CompressionType.GZIP
import static org.prebid.server.functional.model.bidder.CompressionType.NONE
import static org.prebid.server.functional.model.response.auction.ErrorType.GENERIC
import static org.prebid.server.functional.util.HttpUtil.CONTENT_ENCODING_HEADER
import static org.prebid.server.functional.util.privacy.CcpaConsent.Signal.ENFORCED

class BidderConfigurationSpec extends BaseSpec {

    private static final Map<String, String> DISABLE_INVALID_BIDDER_CONFIGS = [
            "adapters.audiencenetwork.enabled": "false",
            "adapters.adxcg.enabled"          : "false",
            "adapters.avocet.enabled"         : "false",
            "adapters.pangle.enabled"         : "false",
            "adapters.teqblaze.enabled"       : "false",
            "adapters.ix.enabled"             : "false"
    ]

    private static final Map<String, String> BIDDER_CONFIG = [
            "adapters.${GENERIC.value}.enabled"                                            : "true",
            "adapters.${GENERIC.value}.meta-info.vendor-id"                                : "0",

            "adapters.${GENERIC.value}.aliases.${ALIAS.value}.enabled"                     : "true",
            "adapters.${GENERIC.value}.aliases.${ALIAS.value}.meta-info.vendor-id"         : "0",
            "adapters.${GENERIC.value}.aliases.${ALIAS.value}.modifying-vast-xml-allowed"  : "true",
            "adapters.${GENERIC.value}.aliases.${ALIAS.value}.pbs-enforces-ccpa"           : "true",

            "adapters.${GENERIC.value}.aliases.${GENER_X.value}.enabled"                   : "true",
            "adapters.${GENERIC.value}.aliases.${GENER_X.value}.meta-info.vendor-id"       : "0",
            "adapters.${GENERIC.value}.aliases.${GENER_X.value}.modifying-vast-xml-allowed": "false",
            "adapters.${GENERIC.value}.aliases.${GENER_X.value}.pbs-enforces-ccpa"         : "false"
    ]

    private static final Map<String, String> ENABLED_DEFAULTS_CONFIG = BIDDER_CONFIG + [
            "adapter-defaults.enabled"                   : "true",
            "adapter-defaults.modifying-vast-xml-allowed": "true",
            "adapter-defaults.pbs-enforces-ccpa"         : "true",
            "adapters.generic.endpoint-compression"     : GZIP.value
    ] + DISABLE_INVALID_BIDDER_CONFIGS

    private static final Map<String, String> DISABLED_DEFAULTS_CONFIG = BIDDER_CONFIG + [
            "adapter-defaults.enabled"                   : "false",
            "adapter-defaults.modifying-vast-xml-allowed": "false",
            "adapter-defaults.pbs-enforces-ccpa"         : "false",
            "adapters.generic.endpoint-compression"     : NONE.value
    ]

    private static final Map<String, String> BIDDER_DISABLED_DEFAULTS_ENABLED_CONFIG = [
            "adapter-defaults.enabled": "true",
            "adapters.generic.enabled": "false"
    ] + DISABLE_INVALID_BIDDER_CONFIGS

    private static final Map<String, String> BIDDER_DISABLED_DEFAULTS_DISABLED_CONFIG = [
            "adapter-defaults.enabled": "false",
            "adapters.generic.enabled": "false"
    ]

    private static final enabledDefaultService = pbsServiceFactory.getService(ENABLED_DEFAULTS_CONFIG)
    private static final disabledDefaultService = pbsServiceFactory.getService(DISABLED_DEFAULTS_CONFIG)

    def "PBS should reject disabled generic bidder regardless of adapter defaults"() {
        given: "PBS with disabled generic bidder"
        def pbsService = pbsServiceFactory.getService(pbsConfig)
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response contains bidder error"
        assert response.ext?.errors[GENERIC]*.code == [2]

        where:
        pbsConfig << [BIDDER_DISABLED_DEFAULTS_ENABLED_CONFIG, BIDDER_DISABLED_DEFAULTS_DISABLED_CONFIG]
    }

    def "PBS should call enabled generic bidder regardless of adapter defaults"() {
        given: "Default bidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder is called"
        assert response.ext?.debug?.httpcalls[GENERIC.value]

        and: "Response has no errors"
        assert !response.ext?.errors

        where:
        pbsService << [enabledDefaultService, disabledDefaultService]
    }

    def "PBS should modify VAST XML when bidder allows modification regardless of adapter defaults"() {
        given: "Vtrack request for alias"
        def payload = PBSUtils.randomString
        def request = VtrackRequest.getDefaultVtrackRequest(encodeXml(Vast.getDefaultVastModel(payload))).tap {
            puts[0].bidder = ALIAS.value
        }
        def accountId = PBSUtils.randomNumber

        and: "Account in DB"
        accountDao.save(new Account(uuid: accountId, eventsEnabled: true))

        when: "PBS processes vtrack request"
        pbsService.sendPostVtrackRequest(request, accountId.toString())

        then: "Cached VAST contains impression tracking"
        def cachedVast = prebidCache.getXmlRecordedRequestsBody(payload)
        assert cachedVast.size() == 1
        assert cachedVast.first().contains("${Endpoint.EVENT}?t=imp&b=${request.puts[0].bidid}&a=$accountId&bidder=${request.puts[0].bidder}")

        where:
        pbsService << [enabledDefaultService, disabledDefaultService]
    }

    def "PBS should preserve VAST XML when bidder disallows modification regardless of adapter defaults"() {
        given: "Vtrack request for alias"
        def payload = PBSUtils.randomString
        def request = VtrackRequest.getDefaultVtrackRequest(encodeXml(Vast.getDefaultVastModel(payload))).tap {
            puts[0].bidder = GENER_X.value
        }
        def accountId = PBSUtils.randomNumber

        and: "Account in DB"
        accountDao.save(new Account(uuid: accountId, eventsEnabled: true))

        when: "PBS processes vtrack request"
        pbsService.sendPostVtrackRequest(request, accountId.toString())

        then: "Cached VAST does not contain impression tracking"
        def cachedVast = prebidCache.getXmlRecordedRequestsBody(payload)
        assert cachedVast.size() == 1
        assert !cachedVast.first().contains("${Endpoint.EVENT}?t=imp&b=${request.puts[0].bidid}&a=$accountId&bidder=${request.puts[0].bidder}")

        where:
        pbsService << [enabledDefaultService, disabledDefaultService]
    }

    def "PBS should mask CCPA-sensitive geo for bidder with CCPA enforcement enabled"() {
        given: "Bid request with CCPA consent and geo"
        def lat = PBSUtils.getRandomDecimal(0, 90)
        def lon = PBSUtils.getRandomDecimal(0, 90)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.generic = null
            imp[0].ext.prebid.bidder.alias = new Generic()
            regs.usPrivacy = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
            device = new Device(geo: new Geo(lat: lat, lon: lon))
        }

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder receives masked coordinates"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device?.geo?.lat as BigDecimal == PBSUtils.roundDecimal(lat, 2)
        assert bidderRequest.device?.geo?.lon as BigDecimal == PBSUtils.roundDecimal(lon, 2)

        where:
        pbsService << [enabledDefaultService, disabledDefaultService]
    }

    def "PBS should preserve CCPA-sensitive geo for bidder with CCPA enforcement disabled"() {
        given: "Bid request with CCPA consent and geo"
        def lat = PBSUtils.getRandomDecimal(0, 90) as float
        def lon = PBSUtils.getRandomDecimal(0, 90) as float
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.generic = null
            imp[0].ext.prebid.bidder.generX = new Generic()
            regs.usPrivacy = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
            device = new Device(geo: new Geo(lat: lat, lon: lon))
        }

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder receives original coordinates"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device?.geo?.lat == lat
        assert bidderRequest.device?.geo?.lon == lon

        where:
        pbsService << [enabledDefaultService, disabledDefaultService]
    }

    def "PBS should gzip bidder request when endpoint compression is gzip"() {
        given: "Default bidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS processes auction request"
        def response = enabledDefaultService.sendAuctionRequest(bidRequest)

        then: "Bidder request contains gzip content encoding"
        assert response.ext?.debug?.httpcalls?.get(GENERIC.value)?.requestHeaders?.first()
                ?.get(CONTENT_ENCODING_HEADER)?.first() == GZIP.value
    }

    def "PBS should omit content encoding when endpoint compression is none"() {
        given: "Default bidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS processes auction request"
        def response = disabledDefaultService.sendAuctionRequest(bidRequest)

        then: "Bidder request has no content encoding"
        assert !response.ext?.debug?.httpcalls?.get(GENERIC.value)?.requestHeaders?.first()
                ?.get(CONTENT_ENCODING_HEADER)
    }

    def "PBS should forward configured server information to bidder"() {
        given: "PBS with server info configuration"
        def serverDataCenter = PBSUtils.randomString
        def serverExternalUrl = "https://${PBSUtils.randomString}.com/".toString()
        def serverHostVendorId = PBSUtils.randomNumber
        def pbsConfig = [
                "datacenter-region"  : serverDataCenter,
                "external-url"       : serverExternalUrl,
                "gdpr.host-vendor-id": serverHostVendorId.toString()
        ]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request contains configured server information"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.ext?.prebid?.server?.externalUrl == serverExternalUrl
        assert bidderRequest.ext.prebid.server.datacenter == serverDataCenter
        assert bidderRequest.ext.prebid.server.gvlId == serverHostVendorId
    }
}
