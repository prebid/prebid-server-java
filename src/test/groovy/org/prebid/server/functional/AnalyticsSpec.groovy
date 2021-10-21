package org.prebid.server.functional

import org.prebid.server.functional.model.mock.services.pubstack.PubStackResponse
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.testcontainers.Dependencies
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.testcontainers.scaffolding.PubStackAnalytics
import spock.lang.Shared

@PBSTest
class AnalyticsSpec extends BaseSpec {

    private static final String scopeId = UUID.randomUUID()

    @Shared
    PubStackAnalytics analytics = new PubStackAnalytics(Dependencies.networkServiceContainer, mapper).tap {
        it.setResponse(PubStackResponse.getDefaultPubStackResponse(scopeId, Dependencies.networkServiceContainer.rootUri))
    }

    def "PBS should send PubStack analytics when analytics.pubstack.enabled=true"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(pbsServiceFactory.pubstackAnalyticsConfig(scopeId))

        and: "Basic bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Initial request count"
        def analyticsRequestCount = analytics.requestCount

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should call pubstack analytics"
        assert analytics.checkRequestCount(analyticsRequestCount + 1, 3000)
    }
}
