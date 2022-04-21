package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.bidder.Rubicon
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.logging.httpinteraction.HttpInteractionRequest
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Retry

import java.time.Instant

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.RUBICON

@PBSTest
class HttpInteractionSpec extends BaseSpec {

    @Retry
    def "PBS should only log request to the specified adapter"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default httpInteractionRequest with specified bidder"
        def request = HttpInteractionRequest.defaultHttpInteractionRequest
        request.bidder = GENERIC

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.imp.first().ext.prebid.bidder.generic = new Generic()
        bidRequest.imp.first().ext.prebid.bidder.rubicon = new Rubicon(accountId: PBSUtils.randomNumber,
                siteId: PBSUtils.randomNumber, zoneId: PBSUtils.randomNumber)

        when: "PBS processes bidders params request"
        defaultPbsService.sendLoggingHttpInteractionRequest(request)

        and: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "There should be only one call to bidder"
        assert bidder.getRequestCount(bidRequest.id) == 1

        and: "PBS log should contain request to allowed adapter"
        def logs = defaultPbsService.getLogsByTime(startTime)
        def genericBidderLogs = getLogsByBidder(logs, GENERIC)
        def rubiconBidderLogs = getLogsByBidder(logs, RUBICON)
        assert getLogsByText(genericBidderLogs, bidRequest.id).size() == 1
        assert getLogsByText(rubiconBidderLogs, bidRequest.id).size() == 0
    }

    def "PBS should not log request to adapter when it is not allowed"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.imp.first().ext.prebid.bidder.generic = new Generic()

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS log should not contain request to adapter"
        def logs = defaultPbsService.getLogsByTime(startTime)
        def genericBidderLogs = getLogsByBidder(logs, GENERIC)
        assert getLogsByText(genericBidderLogs, bidRequest.id).size() == 0
    }

    def "PBS log request to specific adapter should contain only bid params for the named bidder"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default httpInteractionRequest with specified bidder"
        def request = HttpInteractionRequest.defaultHttpInteractionRequest
        request.bidder = GENERIC

        and: "Default basic generic BidRequest with rubicon"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.imp.first().ext.prebid.bidder.generic = new Generic()
        bidRequest.imp.first().ext.prebid.bidder.rubicon = new Rubicon(accountId: PBSUtils.randomNumber,
                siteId: PBSUtils.randomNumber, zoneId: PBSUtils.randomNumber)

        when: "PBS processes bidders params request"
        defaultPbsService.sendLoggingHttpInteractionRequest(request)

        and: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Extract request from logs"
        def logs = defaultPbsService.getLogsByTime(startTime)
        assert logs.size() > 0
        def genericBidderLogs = getLogsByBidder(logs, GENERIC)
        assert genericBidderLogs.size() > 0
        def idLogs = getLogsByText(genericBidderLogs, bidRequest.id)
        assert idLogs.size() == 1

        def requestLog = getRequestFromLog(idLogs.first(), request.bidder.value)
        def retrievedRequest = mapper.decode(requestLog, BidRequest)

        and: "Logged request should not contain bidder parameters in imp[].ext.prebid.bidder.BIDDER"
        assert !retrievedRequest?.imp?.first()?.ext?.prebid?.bidder?.generic
        assert !retrievedRequest?.imp?.first()?.ext?.prebid?.bidder?.rubicon

        and: "Logged request should contain bidder parameters in imp[].ext.BIDDER"
        assert retrievedRequest?.imp?.first()?.ext?.generic

        and: "Logged request should contain only bidder parameters for the named bidder"
        assert !retrievedRequest?.imp?.first()?.ext?.rubicon
    }

    private static List<String> getLogsByBidder(List<String> logs, BidderName bidderName) {
        logs.findAll { it.contains("Request body to ${bidderName.value}:") }
    }

    private static String getRequestFromLog(String log, String bidderName) {
        def logText = "Request body to ${bidderName}:"

        log.substring(log.indexOf(logText) + logText.length() + 2, log.length() - 1)
           .replace("\n", "")
    }
}
