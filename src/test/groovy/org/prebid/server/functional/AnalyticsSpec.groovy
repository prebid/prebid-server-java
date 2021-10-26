package org.prebid.server.functional

import org.prebid.server.functional.model.mock.services.pubstack.PubStackResponse
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.Dependencies
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.testcontainers.scaffolding.PubStackAnalytics
import spock.lang.Ignore
import spock.lang.Shared

@PBSTest
class AnalyticsSpec extends BaseSpec {

    private static final String SCOPE_ID = UUID.randomUUID()
    private static final PrebidServerService pbsService = pbsServiceFactory.getService(pbsServiceFactory.pubstackAnalyticsConfig(SCOPE_ID))

    @Shared
    PubStackAnalytics analytics = new PubStackAnalytics(Dependencies.networkServiceContainer, mapper).tap {
        it.setResponse(PubStackResponse.getDefaultPubStackResponse(SCOPE_ID, Dependencies.networkServiceContainer.rootUri))
    }

    @Ignore("Currently impossible to make this test pass 100% of the time")
    def "PBS should send PubStack analytics when analytics.pubstack.enabled=true"() {
        given: "Basic bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Initial request count"
        def analyticsRequestCount = analytics.requestCount

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should call pubstack analytics"
        assert analytics.checkRequestCount(analyticsRequestCount + 1)
    }
}
