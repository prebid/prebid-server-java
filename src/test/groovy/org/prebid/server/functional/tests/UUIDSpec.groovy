package org.prebid.server.functional.tests

import org.prebid.server.functional.model.Currency
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.model.request.auction.Source
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE

class UUIDSpec extends BaseSpec {

    private static final Map<String, String> STORED_REQUEST_ID_GEN_ENABLED_CONFIG = [
            "settings.generate-storedrequest-bidrequest-id": "true"
    ]

    private static final Map<String, String> STORED_REQUEST_ID_GEN_DISABLED_CONFIG = [
            "settings.generate-storedrequest-bidrequest-id": "false"
    ]

    private static final PrebidServerService storedRequestIdGenEnabledService = pbsServiceFactory.getService(STORED_REQUEST_ID_GEN_ENABLED_CONFIG)

    private static final PrebidServerService storedRequestIdGenDisabledService = pbsServiceFactory.getService(STORED_REQUEST_ID_GEN_DISABLED_CONFIG)

    def cleanupSpec() {
        pbsServiceFactory.removeContainer(STORED_REQUEST_ID_GEN_ENABLED_CONFIG)
        pbsServiceFactory.removeContainer(STORED_REQUEST_ID_GEN_DISABLED_CONFIG)
    }

    def "PBS auction should generate UUID for APP BidRequest id and merge StoredRequest when bidRequestId = #bidRequestId"() {
        given: "Default bid request with stored request and id"
        def storedRequestId = PBSUtils.randomString
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            id = bidRequestId
            ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
        }

        and: "Flush metrics"
        flushMetrics(pbsService)

        and: "Save storedRequest into DB with cur and id"
        def currencies = [Currency.BOGUS]
        def storedBidRequest = new BidRequest(id: PBSUtils.randomString, cur: currencies)
        def storedRequest = StoredRequest.getStoredRequest(storedRequestId, storedBidRequest)
        storedRequestDao.save(storedRequest)

        when: "Requesting PBS auction"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Metric stored_requests_found should be updated"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert metrics["stored_requests_found"] == 1

        and: "BidResponse should be merged with stored request"
        def bidderRequest = bidder.getBidderRequest(bidResponse.id)
        assert bidderRequest.cur.first() == currencies[0]

        and: "Actual bid request ID should be different from incoming bid request id"
        assert bidderRequest.id != bidRequestId

        where:
        bidRequestId          | pbsService
        null                  | storedRequestIdGenDisabledService
        PBSUtils.randomString | storedRequestIdGenEnabledService
        "{{UUID}}"            | storedRequestIdGenDisabledService
    }

    def "PBS auction shouldn't generate UUID for BidRequest id when BidRequest doesn't have APP"() {
        given: "Default bid request with stored request and id"
        def bidRequestId = PBSUtils.randomString
        def storedRequestId = PBSUtils.randomString
        def bidRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            id = bidRequestId
            ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
        }

        and: "Save storedRequest into DB with cur and id"
        def currencies = [Currency.BOGUS]
        def storedBidRequest = new BidRequest(id: PBSUtils.randomString, cur: currencies)
        def storedRequest = StoredRequest.getStoredRequest(storedRequestId, storedBidRequest)
        storedRequestDao.save(storedRequest)

        when: "Requesting PBS auction"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "BidResponse should be merged with stored request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.cur.first() == currencies[0]

        and: "BidderRequest and bidRequest ids should be equal"
        assert bidderRequest.id == bidRequestId
    }

    def "PBS amp should generate UUID for BidRequest id and merge StoredRequest when bidRequestId = #bidRequestId"() {
        given: "Default AMP request with custom Id"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            tagId = bidRequestId
        }

        and: "Default BidRequest"
        def ampStoredRequest = BidRequest.defaultBidRequest

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = pbsService.sendAmpRequest(ampRequest)

        then: "Actual bid request ID should be different from incoming bid request id"
        def requestId = ampResponse.ext?.debug?.resolvedRequest?.id
        def bidderRequest = bidder.getBidderRequest(requestId)
        assert bidderRequest.id != bidRequestId

        where:
        bidRequestId          | pbsService
        PBSUtils.randomString | storedRequestIdGenEnabledService
        "{{UUID}}"            | storedRequestIdGenDisabledService
    }

    def "PBS auction should generate UUID for source.tid and merge StoredRequest when sourceTid= #sourceTid"() {
        given: "Default bid request with stored request and id"
        def storedRequestId = PBSUtils.randomString
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            source = new Source(tid: sourceTid)
            ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
        }

        and: "Flush metrics"
        flushMetrics(pbsService)

        and: "Save storedRequest into DB with cur and source.tid"
        def currencies = [Currency.BOGUS]
        def storedBidRequest = new BidRequest(cur: currencies, source: new Source(tid: PBSUtils.randomString))
        def storedRequest = StoredRequest.getStoredRequest(storedRequestId, storedBidRequest)
        storedRequestDao.save(storedRequest)

        when: "Requesting PBS auction"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Metric stored_requests_found should be updated"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert metrics["stored_requests_found"] == 1

        and: "BidResponse should be merged with stored request"
        def bidderRequest = bidder.getBidderRequest(bidResponse.id)
        assert bidderRequest.cur.first() == currencies[0]

        and: "Actual source.tid should be different from incoming source.tid"
        assert bidderRequest.source.tid != sourceTid

        where:
        sourceTid             | pbsService
        null                  | storedRequestIdGenDisabledService
        "{{UUID}}"            | storedRequestIdGenDisabledService
        PBSUtils.randomString | storedRequestIdGenEnabledService
    }

    def "PBS amp should generate UUID for source.tid id and merge StoredRequest when sourceTid = #sourceTid"() {
        given: "Default AMP request with custom Id"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default StoredBidRequest"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            source = new Source(tid: sourceTid)
        }

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = pbsService.sendAmpRequest(ampRequest)

        then: "Actual bid request ID should be different from incoming bid request id"
        def requestId = ampResponse.ext?.debug?.resolvedRequest?.id
        def bidderRequest = bidder.getBidderRequest(requestId)
        assert bidderRequest.source.tid != sourceTid

        where:
        sourceTid             | pbsService
        null                  | storedRequestIdGenDisabledService
        PBSUtils.randomString | storedRequestIdGenEnabledService
        "{{UUID}}"            | storedRequestIdGenDisabledService
    }

    def "PBS auction should generate UUID for imp[].ext.tid and merge StoredRequest when impExtTid = #impExtTid"() {
        given: "Default bid request with stored request and id"
        def storedRequestId = PBSUtils.randomString
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            imp[0].ext.tid = impExtTid
            ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
        }

        and: "Flush metrics"
        flushMetrics(pbsService)

        and: "Save storedRequest into DB with cur and source.tid"
        def currencies = [Currency.BOGUS]
        def storedBidRequest = new BidRequest(cur: currencies)
        def storedRequest = StoredRequest.getStoredRequest(storedRequestId, storedBidRequest)
        storedRequestDao.save(storedRequest)

        when: "Requesting PBS auction"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Metric stored_requests_found should be updated"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert metrics["stored_requests_found"] == 1

        and: "BidResponse should be merged with stored request"
        def bidderRequest = bidder.getBidderRequest(bidResponse.id)
        assert bidderRequest.cur.first() == currencies[0]

        and: "Actual source.tid should be different from incoming source.tid"
        assert bidderRequest.imp[0].ext.tid != impExtTid

        where:
        impExtTid             | pbsService
        null                  | storedRequestIdGenDisabledService
        PBSUtils.randomString | storedRequestIdGenEnabledService
        "{{UUID}}"            | storedRequestIdGenDisabledService
    }

    def "PBS amp should generate UUID for imp[].ext.tid id and merge StoredRequest when impExtTid = #impExtTid"() {
        given: "Default AMP request with custom Id"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default StoredBidRequest"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.tid = impExtTid
        }

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = pbsService.sendAmpRequest(ampRequest)

        then: "Actual bid request ID should be different from incoming bid request id"
        def requestId = ampResponse.ext?.debug?.resolvedRequest?.id
        def bidderRequest = bidder.getBidderRequest(requestId)
        assert bidderRequest.imp[0].ext.tid != impExtTid

        where:
        impExtTid             | pbsService
        null                  | storedRequestIdGenDisabledService
        PBSUtils.randomString | storedRequestIdGenEnabledService
        "{{UUID}}"            | storedRequestIdGenDisabledService
    }

    def "PBS should generate UUID for empty imp[].id when generate-storedrequest-bidrequest-id = true"() {
        given: "Default bid request with stored request and id"
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            imp[1] = Imp.defaultImpression.tap {
                it.id = null
            }
        }

        when: "Requesting PBS auction"
        def bidResponse = storedRequestIdGenEnabledService.sendAuctionRequest(bidRequest)

        then: "BidResponse should generate UUID for imp[].id"
        def bidderRequest = bidder.getBidderRequest(bidResponse.id)
        assert bidderRequest.imp[1].id.length() == 16
    }

    def "PBS auction should re-assign UUID for all imp[].id and merge StoredRequest when imp[].id not different and generate-storedrequest-bidrequest-id = true"() {
        given: "Default bid request with stored request and id"
        def storedRequestId = PBSUtils.randomString
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            def imp = Imp.defaultImpression.tap {
                id = PBSUtils.randomString
            }
            addImp(imp)
            addImp(imp)
            ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
        }

        and: "Flush metrics"
        flushMetrics(storedRequestIdGenEnabledService)

        and: "Save storedRequest into DB with cur and source.tid"
        def currencies = [Currency.BOGUS]
        def storedBidRequest = new BidRequest(cur: currencies)
        def storedRequest = StoredRequest.getStoredRequest(storedRequestId, storedBidRequest)
        storedRequestDao.save(storedRequest)

        when: "Requesting PBS auction"
        def bidResponse = storedRequestIdGenEnabledService.sendAuctionRequest(bidRequest)

        then: "Metric stored_requests_found should be updated"
        def metrics = storedRequestIdGenEnabledService.sendCollectedMetricsRequest()
        assert metrics["stored_requests_found"] == 1

        and: "BidResponse should be merged with stored request"
        def bidderRequest = bidder.getBidderRequest(bidResponse.id)
        assert bidderRequest.cur.first() == currencies[0]

        and: "Actual imp[].id should be different from incoming imp[].id"
        assert bidderRequest.imp[0].id != bidRequest.imp[0].id
        assert bidderRequest.imp[1].id != bidRequest.imp[1].id
        assert bidderRequest.imp[2].id != bidRequest.imp[2].id

        and: "Re-assign imp[].id should contain id from 1"
        assert bidderRequest.imp[0].id == "1"
        assert bidderRequest.imp[1].id == "2"
        assert bidderRequest.imp[2].id == "3"
    }
}
