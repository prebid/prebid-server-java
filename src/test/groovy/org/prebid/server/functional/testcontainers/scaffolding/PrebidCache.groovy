package org.prebid.server.functional.testcontainers.scaffolding

import com.github.tomakehurst.wiremock.matching.RequestPattern
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.prebid.server.functional.model.request.cache.BidCacheRequest
import org.prebid.server.functional.model.response.vtrack.TransferValue
import org.prebid.server.functional.testcontainers.container.NetworkServiceContainer
import org.prebid.server.functional.util.PBSUtils

import static java.lang.Integer.MAX_VALUE
import static org.mockserver.model.HttpStatusCode.INTERNAL_SERVER_ERROR_500
import static org.mockserver.model.HttpStatusCode.OK_200
import static com.github.tomakehurst.wiremock.client.WireMock.post
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse
import static com.github.tomakehurst.wiremock.client.WireMock.get
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor

class PrebidCache extends NetworkScaffolding {

    private static final String CACHE_ENDPOINT = "/cache"
    private static final String RESPONSE_BODY =
            '''
                 {
                   "responses" : [ 
                    {{#each (jsonPath request.body '$.puts')}}
                   {
                     "uuid" : "{{randomValue type='UUID'}}"
                   }{{#unless @last}},{{/unless}}
                         {{/each}} 
                   ]
                 }
            '''


    PrebidCache(NetworkServiceContainer wireMockContainer) {
        super(wireMockContainer, CACHE_ENDPOINT)
    }

    String getVTracGetRequestParams() {
        getRecordedRequestsQueryParameters(getRequestedFor(urlMatching("^/cache(\\?.*)?\$")))
    }

    int getXmlRequestCount(String payload) {
        getRequestCount(getXmlCacheRequest(payload))
    }

    List<String> getXmlRecordedRequestsBody(String payload) {
        getRecordedRequestsBody(getXmlCacheRequest(payload))
    }

    protected RequestPattern getRequest() {
        postRequestedFor(urlEqualTo(endpoint))
                .build()
    }

    @Override
    protected RequestPatternBuilder getRequest(String impId) {
        postRequestedFor(urlEqualTo(CACHE_ENDPOINT))
                .withRequestBody(matchingJsonPath("\$.puts[?(@.value.impid == '" + impId + "')]"))
    }


    List<BidCacheRequest> getRecordedRequests(String impId) {
        wireMockClient.find(getRequest(impId)).bodyAsString
                .collect { decode(it, BidCacheRequest) }
    }

    Map<String, List<String>> getRequestHeaders(String impId) {
        getLastRecordedRequestHeaders(getRequest(impId))
    }

    @Override
    void setResponse() {
        wireMockClient.register(post(urlPathEqualTo(endpoint))
                .atPriority(MAX_VALUE)
                .willReturn(aResponse()
                        .withTransformers("response-template")
                        .withStatus(OK_200.code())
                        .withBody(RESPONSE_BODY)))
    }

    void setGetResponse(TransferValue vTrackResponse) {
        wireMockClient.register(get(urlPathEqualTo(endpoint))
                .atPriority(MAX_VALUE)
                .willReturn(aResponse()
                        .withStatus(OK_200.code())
                        .withBody(encode(vTrackResponse))))
    }

    void setInvalidPostResponse() {
        wireMockClient.register(post(urlPathEqualTo(endpoint))
                .atPriority(MAX_VALUE)
                .willReturn(aResponse()
                        .withStatus(INTERNAL_SERVER_ERROR_500.code())))
    }

    void setInvalidGetResponse(String uuid, String errorMessage = PBSUtils.randomString) {
        wireMockClient.register(get(urlPathEqualTo(endpoint))
                .withQueryParam("uuid", equalTo(uuid))
                .atPriority(MAX_VALUE)
                .willReturn(aResponse()
                        .withStatus(INTERNAL_SERVER_ERROR_500.code())
                        .withBody(errorMessage)))
    }

    private static RequestPatternBuilder getXmlCacheRequest(String payload) {
        postRequestedFor(urlEqualTo(CACHE_ENDPOINT))
                .withRequestBody(matchingJsonPath("\$.puts[?(@.value =~ /.*" + payload + ".*/)]"))
    }
}
