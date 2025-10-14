package org.prebid.server.functional.testcontainers.scaffolding

import org.mockserver.matchers.TimeToLive
import org.mockserver.matchers.Times
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.prebid.server.functional.model.mock.services.prebidcache.response.CacheObject
import org.prebid.server.functional.model.mock.services.prebidcache.response.PrebidCacheResponse
import org.prebid.server.functional.model.request.cache.BidCacheRequest
import org.testcontainers.containers.MockServerContainer

import java.util.stream.Stream

import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response
import static org.mockserver.model.HttpStatusCode.INTERNAL_SERVER_ERROR_500
import static org.mockserver.model.HttpStatusCode.OK_200
import static org.mockserver.model.JsonPathBody.jsonPath

class PrebidCache extends NetworkScaffolding {

    private static final String CACHE_ENDPOINT = "/cache"

    PrebidCache(MockServerContainer mockServerContainer) {
        super(mockServerContainer, CACHE_ENDPOINT)
    }

    int getVTracGetRequestCount() {
        getRequestCount(request().withMethod("GET")
                .withPath(CACHE_ENDPOINT))
    }

    void setXmlCacheResponse(String payload, PrebidCacheResponse prebidCacheResponse) {
        setResponse(getXmlCacheRequest(payload), prebidCacheResponse)
    }

    int getXmlRequestCount(String payload) {
        getRequestCount(getXmlCacheRequest(payload))
    }

    List<String> getXmlRecordedRequestsBody(String payload) {
        getRecordedRequestsBody(getXmlCacheRequest(payload))
    }

    Map<String, String> getXmlRecordedRequestHeaders(String payload) {
        getLastRecordedRequestHeaders(getXmlCacheRequest(payload))
    }

    @Override
    protected HttpRequest getRequest(String impId) {
        request().withMethod("POST")
                .withPath(CACHE_ENDPOINT)
                .withBody(jsonPath("\$.puts[?(@.value.impid == '$impId')]"))
    }

    List<BidCacheRequest> getRecordedRequests(String impId) {
        mockServerClient.retrieveRecordedRequests(getRequest(impId))
                .collect { decode(it.body.toString(), BidCacheRequest) }
    }

    Map<String, List<String>> getRequestHeaders(String impId) {
        getLastRecordedRequestHeaders(getRequest(impId))
    }

    @Override
    HttpRequest getRequest() {
        request().withMethod("POST")
                .withPath(CACHE_ENDPOINT)
    }

    @Override
    void setResponse() {
        mockServerClient.when(request().withPath(endpoint), Times.unlimited(), TimeToLive.unlimited(), -10)
                .respond { request ->
                    request.withPath(endpoint)
                            ? response().withStatusCode(OK_200.code()).withBody(getBodyByRequest(request))
                            : HttpResponse.notFoundResponse()
                }
    }

    void setInvalidPostResponse() {
        mockServerClient.when(request().withPath(endpoint), Times.unlimited(), TimeToLive.unlimited(), -10)
                .respond { response().withStatusCode(INTERNAL_SERVER_ERROR_500.code()) }
    }

    void setVtrackResponse(String uuid) {
        mockServerClient.when(request()
                .withMethod("GET")
                .withPath(endpoint)
                .withQueryStringParameter("uuid", uuid), Times.unlimited(), TimeToLive.unlimited(), -10)
                .respond { request ->
                    request.withPath(endpoint)
                            ? response().withStatusCode(OK_200.code())
                            : HttpResponse.notFoundResponse()
                }
    }

    void setInvalidVtrackResponse(String uuid) {
        mockServerClient.when(request()
                .withMethod("GET")
                .withPath(endpoint)
                .withQueryStringParameter("uuid", uuid), Times.unlimited(), TimeToLive.unlimited(), -10)
                .respond { response().withStatusCode(INTERNAL_SERVER_ERROR_500.code()) }

    }

    private static HttpRequest getXmlCacheRequest(String payload) {
        request().withMethod("POST")
                .withPath(CACHE_ENDPOINT)
                .withBody(jsonPath("\$.puts[?(@.value =~/^.*$payload.*\$/)]"))
    }

    private String getBodyByRequest(HttpRequest request) {
        def requestString = request.bodyAsString
        def jsonNode = toJsonNode(requestString)
        def putsSize = jsonNode.get("puts").size()
        def cacheObjects = Stream.generate(CacheObject::getDefaultCacheObject)
                .limit(putsSize)
                .toList()
        encode(new PrebidCacheResponse(responses: cacheObjects))
    }
}
