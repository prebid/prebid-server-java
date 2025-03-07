package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.AppNexus
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.bidder.Openx
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST
import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.BOGUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.GENER_X
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.bidder.CompressionType.GZIP
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID
import static org.prebid.server.functional.testcontainers.Dependencies.networkServiceContainer
import static org.prebid.server.functional.util.HttpUtil.CONTENT_ENCODING_HEADER

class AliasSpec extends BaseSpec {

    private static final Map<String, String> ADDITIONAL_BIDDERS_CONFIG = ["adapters.${OPENX.value}.enabled"    : "true",
                                                                          "adapters.${OPENX.value}.endpoint"   : "$networkServiceContainer.rootUri/${OPENX.value}/auction".toString(),
                                                                          "adapters.${APPNEXUS.value}.enabled" : "true",
                                                                          "adapters.${APPNEXUS.value}.endpoint": "$networkServiceContainer.rootUri/${APPNEXUS.value}/auction".toString()]
    private static PrebidServerService pbsServiceWithAdditionalBidders

    def setupSpec() {
        pbsServiceWithAdditionalBidders = pbsServiceFactory.getService(ADDITIONAL_BIDDERS_CONFIG + GENERIC_ALIAS_CONFIG)
    }

    def cleanupSpec() {
        pbsServiceFactory.removeContainer(ADDITIONAL_BIDDERS_CONFIG + GENERIC_ALIAS_CONFIG)
    }

    def "PBS should be able to take alias from request"() {
        given: "Default bid request with alias"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.aliases = [(ALIAS.value): GENERIC]
            imp[0].ext.prebid.bidder.alias = new Generic()
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS contain two http calls and the same url for both"
        def responseDebug = response.ext.debug
        assert responseDebug.httpcalls.size() == 2
        assert responseDebug.httpcalls[GENERIC.value]*.uri == responseDebug.httpcalls[ALIAS.value]*.uri

        and: "Resolved request should contain aliases as in request"
        assert responseDebug.resolvedRequest.ext.prebid.aliases == bidRequest.ext.prebid.aliases

        and: "Bidder request should contain request per-alies"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert bidderRequests.size() == 2
    }

    def "PBS shouldn't apply aliases for bidder when aliases didn't provide proper config"() {
        given: "Default bid request with alias"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.aliases = [(BOGUS.value): GENERIC]
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS contain one bidder requested"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert bidderRequests.size() == 1

        and: "Resolved request should contain unknown aliases as in request"
        assert response.ext.debug.resolvedRequest.ext.prebid.aliases == bidRequest.ext.prebid.aliases
    }

    def "PBS should apply compression type for bidder alias when adapters.BIDDER.endpoint-compression = gzip"() {
        given: "PBS with adapter configuration"
        def compressionType = GZIP.value
        def pbsConfig = ["adapters.generic.endpoint-compression": compressionType]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request with alias"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.aliases = [(ALIAS.value): GENERIC]
            imp[0].ext.prebid.bidder.alias = new Generic()
        }

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain header Content-Encoding = gzip"
        assert response.ext?.debug?.httpcalls?.get(ALIAS.value)?.requestHeaders?.first()
                ?.get(CONTENT_ENCODING_HEADER)?.first() == compressionType

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should return an error when GVL Id alias refers to unknown bidder alias"() {
        given: "Default basic BidRequest with aliasgvlids and aliases"
        def bidderName = PBSUtils.randomString
        def validId = 1
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.aliasgvlids = [(bidderName): validId]
        bidRequest.ext.prebid.aliases = [(PBSUtils.randomString): GENERIC]

        when: "Sending auction request to PBS"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.responseBody.contains("Invalid request format: request.ext.prebid.aliasgvlids. " +
                "vendorId ${validId} refers to unknown bidder alias: ${bidderName}")
    }

    def "PBS should return an error when GVL ID alias value is lower that one"() {
        given: "Default basic BidRequest with aliasgvlids and aliases"
        def bidderName = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.aliasgvlids = [(bidderName): invalidId]
        bidRequest.ext.prebid.aliases = [(bidderName): GENERIC]

        when: "Sending auction request to PBS"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.responseBody.contains("Invalid request format: request.ext.prebid.aliasgvlids. " +
                "Invalid vendorId ${invalidId} for alias: ${bidderName}. Choose a different vendorId, or remove this entry.")

        where:
        invalidId << [PBSUtils.randomNegativeNumber, 0]
    }

    def "PBS should emit error when alias didn't participate in request"() {
        given: "Default bid request with alias"
        def randomString = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.aliases = [(randomString): BOGUS]
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request should fail with an error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == BAD_REQUEST.code()
        assert exception.responseBody == "Invalid request format: request.ext.prebid.aliases.$randomString " +
                "refers to unknown bidder: $BOGUS.value"
    }

    def "PBS aliased bidder config should be independently from parent"() {
        given: "Default bid request with alias"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.alias = new Generic()
        }

        when: "PBS processes auction request"
        pbsServiceWithAdditionalBidders.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain request per-alies"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert bidderRequests.size() == 2
    }

    def "PBS should ignore aliases for requests with a base adapter"() {
        given: "PBs server with aliases config"
        def pbsConfig = ["adapters.openx.enabled" : "true",
                         "adapters.openx.endpoint": "$networkServiceContainer.rootUri/openx/auction".toString()]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request with openx and alias bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            imp[0].ext.prebid.bidder.generic = new Generic()
            ext.prebid.aliases = [(OPENX.value): GENERIC]
        }

        when: "PBS processes auction request"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS contain two http calls and the different url for both"
        def responseDebug = bidResponse.ext.debug
        assert responseDebug.httpcalls.size() == 2
        assert responseDebug.httpcalls[OPENX.value]*.uri == ["$networkServiceContainer.rootUri/openx/auction"]
        assert responseDebug.httpcalls[GENERIC.value]*.uri == ["$networkServiceContainer.rootUri/auction"]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should validate request as alias request and without any warnings when required properties in place"() {
        given: "Default bid request with openx and alias bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.appNexus = AppNexus.default
            imp[0].ext.prebid.bidder.generic = null
            ext.prebid.aliases = [(APPNEXUS.value): OPENX]
        }

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithAdditionalBidders.sendAuctionRequest(bidRequest)

        then: "PBS contain http call for specific bidder"
        def responseDebug = bidResponse.ext.debug
        assert responseDebug.httpcalls.size() == 1
        assert responseDebug.httpcalls[APPNEXUS.value]*.uri == ["$networkServiceContainer.rootUri/${APPNEXUS.value}/auction"]

        and: "PBS should not contain any warnings"
        assert !bidResponse.ext.warnings
    }

    def "PBS should validate request as alias request and emit proper warnings when validation fails for request"() {
        given: "Default bid request with openx and alias bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.appNexus = new AppNexus()
            imp[0].ext.prebid.bidder.generic = null
            ext.prebid.aliases = [(APPNEXUS.value): OPENX]
        }

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithAdditionalBidders.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't contain http calls"
        assert !bidResponse.ext.debug.httpcalls

        and: "Bid response should contain warning"
        assert bidResponse.ext?.warnings[PREBID]?.code == [999, 999]
        assert bidResponse.ext?.warnings[PREBID]*.message ==
                ["WARNING: request.imp[0].ext.prebid.bidder.${APPNEXUS.value} was dropped with a reason: " +
                         "request.imp[0].ext.prebid.bidder.${APPNEXUS.value} failed validation.\n" +
                         "\$: must be valid to one and only one schema, but 0 are valid\n" +
                         "\$: required property 'placement_id' not found\n" +
                         "\$: required property 'inv_code' not found\n" +
                         "\$: required property 'placementId' not found\n" +
                         "\$: required property 'member' not found\n" +
                         "\$: required property 'invCode' not found",
                 "WARNING: request.imp[0].ext must contain at least one valid bidder"]
    }

    def "PBS should invoke as aliases when alias is unknown and core bidder is specified"() {
        given: "Default bid request with generic and alias bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.generX = new Generic()
            ext.prebid.aliases = [(GENER_X.value): GENERIC]
        }

        when: "PBS processes auction request"
        def bidResponse = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS contain two http calls and the same url for both"
        def responseDebug = bidResponse.ext.debug
        assert responseDebug.httpcalls.size() == 2
        assert responseDebug.httpcalls[GENER_X.value]*.uri == responseDebug.httpcalls[GENERIC.value]*.uri

        and: "Bidder request should contain request per-alies"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert bidderRequests.size() == 2
    }

    def "PBS should invoke aliases when alias is unknown and no core bidder is specified"() {
        given: "Default bid request with generic and alias bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.generX = new Generic()
            imp[0].ext.prebid.bidder.generic = null
            ext.prebid.aliases = [(GENER_X.value): GENERIC]
        }

        when: "PBS processes auction request"
        def bidResponse = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS contain two http calls and the same url for both"
        def responseDebug = bidResponse.ext.debug
        assert responseDebug.httpcalls.size() == 1
        assert responseDebug.httpcalls[GENER_X.value]
    }
}
