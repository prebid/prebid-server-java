package org.prebid.server.functional.testcontainers.scaffolding

import com.github.tomakehurst.wiremock.matching.RequestPattern
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.prebid.server.functional.model.bidderspecific.BidderRequest
import org.prebid.server.functional.testcontainers.container.NetworkServiceContainer

import static com.github.tomakehurst.wiremock.client.WireMock.post
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse
import static org.prebid.server.functional.model.HttpStatusCode.OK_200

class Bidder extends NetworkScaffolding {

    private static final String DEFAULT_BODY_RESPONSE =
            '''
                    {
                      "id": "{{jsonPath request.body '$.id'}}",
                      "seatbid": [
                        {
                          "bid": [
                            {{#each (jsonPath request.body '$.imp')}}
                            {
                              "id": "bid-{{randomInt}}",
                              "impid": "{{this.id}}",
                              "price": 10.0,
                              {{#if this.banner}}
                              "w": {{this.banner.format.[0].w}},
                              "h": {{this.banner.format.[0].h}},
                              {{/if}}
                              "crid": "creative-{{@index}}"
                            }{{#unless @last}},{{/unless}}
                            {{/each}}
                          ],
                          "seat": "generic"
                        }
                      ]
                    }
            '''

    Bidder(NetworkServiceContainer wireMockContainer, String endpoint = "/auction") {
        super(wireMockContainer, endpoint)
    }

    protected RequestPattern getRequest() {
        postRequestedFor(urlEqualTo(endpoint))
                .build()
    }

    @Override
    protected RequestPatternBuilder getRequest(String bidRequestId) {
        postRequestedFor(urlMatching("^$endpoint(\\?.*)?\$"))
                .withRequestBody(matchingJsonPath("\$.id", equalTo(bidRequestId)))
    }

    RequestPattern getRequest(String bidRequestId, String requestMatchPath) {
        postRequestedFor(urlMatching("^$endpoint(\\?.*)?\$"))
                .withRequestBody(matchingJsonPath("\$[?(@." + requestMatchPath + " == '" + bidRequestId + "')]"))
                .build()
    }

    @Override
    void setResponse() {
        wireMockClient.register(post(urlPathEqualTo(endpoint))
                .atPriority(Integer.MAX_VALUE)
                .willReturn(aResponse()
                        .withTransformers("response-template")
                        .withStatus(OK_200.code)
                        .withBody(DEFAULT_BODY_RESPONSE)))
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
}
