package org.prebid.server.functional.testcontainers.scaffolding

import org.mockserver.matchers.TimeToLive
import org.mockserver.matchers.Times
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.prebid.server.functional.model.bidderspecific.BidderRequest
import org.prebid.server.functional.model.request.auction.Banner
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Format
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.response.auction.BidResponse
import org.testcontainers.containers.MockServerContainer

import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response
import static org.mockserver.model.HttpStatusCode.OK_200
import static org.mockserver.model.JsonPathBody.jsonPath

class Bidder extends NetworkScaffolding {

    Bidder(MockServerContainer mockServerContainer, String endpoint = "/auction") {
        super(mockServerContainer, endpoint)
    }

    @Override
    protected HttpRequest getRequest(String bidRequestId) {
        request().withPath(endpoint)
                 .withBody(jsonPath("\$[?(@.id == '$bidRequestId')]"))
    }

    @Override
    protected HttpRequest getRequest() {
        request().withPath(endpoint)
    }

    HttpRequest getRequest(String bidRequestId, String requestMatchPath) {
        request().withPath(endpoint)
                 .withBody(jsonPath("\$[?(@.$requestMatchPath == '$bidRequestId')]"))
    }

    @Override
    void setResponse() {
        mockServerClient.when(request().withPath(endpoint), Times.unlimited(), TimeToLive.unlimited(), -10)
                        .respond {request -> request.withPath(endpoint)
                                ? response().withStatusCode(OK_200.code()).withBody(getBodyByRequest(request))
                                : HttpResponse.notFoundResponse()}
    }

    List<BidderRequest> getBidderRequests(String bidRequestId) {
        getRecordedRequestsBody(bidRequestId).collect { decode(it, BidderRequest) }
    }

    BidderRequest getBidderRequest(String bidRequestId) {
        def bidderRequests = getBidderRequests(bidRequestId)
        def bidderCallCount = bidderRequests.size()

        if (bidderCallCount != 1) {
            throw new IllegalStateException("Expecting exactly 1 bidder call. Got $bidderCallCount")
        }

        bidderRequests.first()
    }

    Map<String, List<String>> getLastRecordedBidderRequestHeaders(String bidRequestId) {
        return getLastRecordedRequestHeaders(bidRequestId)
    }

    private static String getBodyByRequest(HttpRequest request) {
        def requestString = request.bodyAsString
        def jsonNode = toJsonNode(requestString)
        def id = jsonNode.get("id").asText()
        def impNode = jsonNode.get("imp")
        def imps = impNode.collect {
            def formatNode = it.get("banner") != null ? it.get("banner").get("format") : null
            new Imp(id: it.get("id").asText(),
                    banner: formatNode != null
                            ? new Banner(format: [new Format(weight: formatNode.first().get("w").asInt(), height: formatNode.first().get("h").asInt())])
                            : null)}
        def bidRequest = new BidRequest(id: id, imp: imps)
        def response = BidResponse.getDefaultBidResponse(bidRequest)
        encode(response)
    }
}
