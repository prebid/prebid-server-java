package org.prebid.server.functional.tests.module.id5userid

import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.mockserver.model.MediaType
import org.prebid.server.functional.model.bidder.AppNexus
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.model.request.auction.Uid
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.scaffolding.Bidder
import org.prebid.server.functional.testcontainers.scaffolding.NetworkScaffolding
import org.prebid.server.functional.tests.module.ModuleBaseSpec
import org.testcontainers.containers.MockServerContainer

import static org.prebid.server.functional.model.ModuleName.ID5_USER_ID
import static org.prebid.server.functional.testcontainers.Dependencies.networkServiceContainer

class Id5UserIdModuleSpec extends ModuleBaseSpec {

    private static final String ID5_SOURCE = "id5-sync.com"
    private static final String TEST_ID5_VALUE = "ID5*test-id5-user-id"
    private static final String BLOCKED_ACCOUNT = "blocked-account"

    private static final String ALIAS_ENDPOINT = "/alias-auction"
    private static final String APPNEXUS_ENDPOINT = "/appnexus-auction"

    private static final Id5FetchService id5FetchService = new Id5FetchService(networkServiceContainer)
    private static final Bidder aliasBidder = new Bidder(networkServiceContainer, ALIAS_ENDPOINT)
    private static final Bidder appnexusBidder = new Bidder(networkServiceContainer, APPNEXUS_ENDPOINT)

    private static final Map<String, String> CONFIG =
            getId5UserIdSettings([
                    "hooks.${ID5_USER_ID.code}.bidder-filter.exclude" : "false",
                    "hooks.${ID5_USER_ID.code}.bidder-filter.values"  : "generic,alias",
                    "hooks.${ID5_USER_ID.code}.account-filter.exclude": "true",
                    "hooks.${ID5_USER_ID.code}.account-filter.values" : BLOCKED_ACCOUNT
            ]) + [
                    "adapters.generic.aliases.alias.enabled" : "true",
                    "adapters.generic.aliases.alias.endpoint": "${networkServiceContainer.rootUri}${ALIAS_ENDPOINT}".toString(),
                    "adapters.appnexus.enabled"              : "true",
                    "adapters.appnexus.endpoint"             : "${networkServiceContainer.rootUri}${APPNEXUS_ENDPOINT}".toString()
            ]

    private static final PrebidServerService pbsService = pbsServiceFactory.getService(CONFIG)

    def setupSpec() {
        aliasBidder.setResponse()
        appnexusBidder.setResponse()
    }

    def setup() {
        id5FetchService.reset()
        id5FetchService.setFetchResponse(TEST_ID5_VALUE)
    }

    def cleanupSpec() {
        pbsServiceFactory.removeContainer(CONFIG)
        aliasBidder.reset()
        appnexusBidder.reset()
        id5FetchService.reset()
    }

    def "PBS should fetch ID5 and inject EIDs into allowed bidders and skip filtered-out bidders"() {
        given: "Bid request with all bidders"
        def bidRequest = createBidRequest()

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "ID5 fetch endpoint should be called once"
        assert id5FetchService.requestCount == 1

        and: "Allowed bidders should receive request with ID5 EID"
        def genericBidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyId5EidPresent(genericBidderRequest.user, TEST_ID5_VALUE)
        def aliasBidderRequest = aliasBidder.getBidderRequest(bidRequest.id)
        verifyId5EidPresent(aliasBidderRequest.user, TEST_ID5_VALUE)

        and: "Not allowed bidder should not receive ID5 EID"
        def appnexusBidderRequest = appnexusBidder.getBidderRequest(bidRequest.id)
        verifyId5EidAbsent(appnexusBidderRequest.user)
    }

    def "PBS should skip ID5 fetch when ID5 EID already present in request"() {
        given: "Bid request with existing ID5 EID"
        def existingId5Value = "existing-id5-value"
        def bidRequest = createBidRequest().tap {
            user = new User(eids: [new Eid(source: ID5_SOURCE, uids: [new Uid(id: existingId5Value, atype: 1)])])
        }

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "ID5 fetch endpoint should not be called"
        assert id5FetchService.requestCount == 0

        and: "All bidders should receive request with original ID5 EID"
        verifyId5EidPresent(bidder.getBidderRequest(bidRequest.id).user, existingId5Value, false)
        verifyId5EidPresent(aliasBidder.getBidderRequest(bidRequest.id).user, existingId5Value, false)
        verifyId5EidPresent(appnexusBidder.getBidderRequest(bidRequest.id).user, existingId5Value, false)
    }

    def "PBS should skip ID5 fetch for blocked account"() {
        given: "Bid request with blocked account"
        def bidRequest = createBidRequest().tap {
            accountId = BLOCKED_ACCOUNT
        }

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "ID5 fetch endpoint should not be called"
        assert id5FetchService.requestCount == 0

        and: "All bidders should receive request without ID5 EID"
        verifyId5EidAbsent(bidder.getBidderRequest(bidRequest.id).user)
        verifyId5EidAbsent(aliasBidder.getBidderRequest(bidRequest.id).user)
        verifyId5EidAbsent(appnexusBidder.getBidderRequest(bidRequest.id).user)
    }

    private static BidRequest createBidRequest() {
        BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.alias = new Generic()
            imp[0].ext.prebid.bidder.appNexus = AppNexus.default
        }
    }

    private static void verifyId5EidPresent(User user, String expectedId5Value, boolean expectInserter = true) {
        def eids = user?.ext?.eids ?: user?.eids
        assert eids, "Expected EIDs to be present in bidder request"
        def id5Eid = eids.find { it.source == ID5_SOURCE }
        assert id5Eid, "Expected ID5 EID with source '$ID5_SOURCE'"
        assert id5Eid.uids[0].id == expectedId5Value
        if (expectInserter) {
            assert id5Eid.inserter == "prebid-server"
        }
    }

    private static void verifyId5EidAbsent(User user) {
        def eids = user?.ext?.eids ?: user?.eids
        if (eids) {
            assert !eids.any { it.source == ID5_SOURCE }, "Expected no ID5 EID in bidder request"
        }
    }

    static class Id5FetchService extends NetworkScaffolding {

        private static final String ID5_FETCH_ENDPOINT = "/id5-fetch"

        Id5FetchService(MockServerContainer mockServerContainer) {
            super(mockServerContainer, ID5_FETCH_ENDPOINT)
        }

        void setFetchResponse(String id5Value) {
            def responseBody = """
            {
              "ids": {
                "id5": {
                  "eid": {
                    "source": "id5-sync.com",
                    "uids": [{
                      "id": "${id5Value}",
                      "atype": 1
                    }]
                  }
                }
              }
            }
            """
            mockServerClient.when(HttpRequest.request().withMethod("POST").withPath("${endpoint}/.*"))
                    .respond(HttpResponse.response().withStatusCode(200)
                            .withBody(responseBody, MediaType.APPLICATION_JSON))
        }

        @Override
        protected HttpRequest getRequest(String value) {
            HttpRequest.request().withMethod("POST").withPath("${endpoint}/${value}")
        }

        @Override
        protected HttpRequest getRequest() {
            HttpRequest.request().withMethod("POST").withPath("${endpoint}/.*")
        }

        @Override
        void reset() {
            super.reset("${endpoint}/.*" as String)
        }

        @Override
        void setResponse() {
            setFetchResponse("ID5*test-id5-user-id")
        }
    }
}
