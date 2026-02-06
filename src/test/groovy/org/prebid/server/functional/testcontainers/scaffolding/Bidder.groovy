package org.prebid.server.functional.testcontainers.scaffolding

import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.mockserver.model.HttpRequest
import org.prebid.server.functional.model.bidderspecific.BidderRequest
import org.prebid.server.functional.model.request.auction.Banner
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Format
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.response.auction.BidResponse
import org.wiremock.integrations.testcontainers.WireMockContainer
import static com.github.tomakehurst.wiremock.client.WireMock.*;

import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.JsonPathBody.jsonPath

class Bidder extends NetworkScaffolding {

    Bidder(WireMockContainer wireMockContainer, String endpoint = "/auction") {
        super(wireMockContainer, endpoint)
    }

    @Override
    protected HttpRequest getRequest(String bidRequestId) {
        request().withPath(endpoint)
                .withBody(jsonPath("\$[?(@.id == '$bidRequestId')]"))
    }

    @Override
    protected HttpRequest getRequest() {
        return request().withPath(endpoint)
    }

    @Override
    protected RequestPatternBuilder getRequestPattern() {
        postRequestedFor(urlEqualTo(endpoint))
    }

    @Override
    protected RequestPatternBuilder getRequestPattern(String bidRequestId) {
        postRequestedFor(urlEqualTo(endpoint))
                .withRequestBody(matchingJsonPath("\$.id", equalTo(bidRequestId)))
    }

    HttpRequest getRequest(String bidRequestId, String requestMatchPath) {
        request().withPath(endpoint)
                .withBody(jsonPath("\$[?(@.$requestMatchPath == '$bidRequestId')]"))
    }

    @Override
    void setResponse() {
        wireMockClient.register(any(urlPathEqualTo(endpoint))
                .atPriority(1)
                .willReturn(aResponse()
                        .withTransformers("response-template")
                        .withStatus(200)
                        .withBody("{\n" +
                                "  \"id\": \"{{jsonPath request.body '\$.id'}}\",\n" +
                                "  \"seatbid\": [\n" +
                                "    {\n" +
                                "      \"bid\": [\n" +
                                "        {\n" +
                                "          \"id\": \"16d8142f-9449-48d7-9a03-42cc12ca49a0\",\n" +
                                "          \"impid\": \"{{jsonPath request.body '\$.imp[0].id'}}\",\n" +
                                "          \"price\": 8.381,\n" +
                                "          \"crid\": \"1\",\n" +
                                "          \"w\": 300,\n" +
                                "          \"h\": 250\n" +
                                "        }\n" +
                                "      ],\n" +
                                "      \"seat\": \"generic\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")
                ))
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

    private String getBodyByRequest(String body) {
        def jsonNode = toJsonNode(body)
        def id = jsonNode.get("id").asText()
        def impNode = jsonNode.get("imp")
        def imps = impNode.collect {
            def formatNode = it.get("banner") != null ? it.get("banner").get("format") : null
            new Imp(id: it.get("id").asText(),
                    banner: formatNode != null
                            ? new Banner(format: [new Format(width: formatNode.first().get("w").asInt(), height: formatNode.first().get("h").asInt())])
                            : null)
        }
        def bidRequest = new BidRequest(id: id, imp: imps)
        def response = BidResponse.getDefaultBidResponse(bidRequest)
        encode(response)
    }
}
