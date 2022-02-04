package org.prebid.server.functional.testcontainers.scaffolding

import org.mockserver.matchers.TimeToLive
import org.mockserver.matchers.Times
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.prebid.server.functional.model.mock.services.prebidcache.response.PrebidCacheResponse
import org.prebid.server.functional.util.ObjectMapperWrapper
import org.testcontainers.containers.MockServerContainer

import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response
import static org.mockserver.model.HttpStatusCode.OK_200
import static org.mockserver.model.JsonPathBody.jsonPath

class PrebidCache extends NetworkScaffolding {

    private static final String CACHE_ENDPOINT = "/cache"

    PrebidCache(MockServerContainer mockServerContainer, ObjectMapperWrapper mapper) {
        super(mockServerContainer, CACHE_ENDPOINT, mapper)
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

    @Override
    HttpRequest getRequest() {
        request().withMethod("POST")
                 .withPath(CACHE_ENDPOINT)
    }

    @Override
    void setResponse() {
        def json = mapper.encode(PrebidCacheResponse.defaultCacheResponse)
        mockServerClient.when(request().withPath(endpoint), Times.unlimited(), TimeToLive.unlimited(), -10)
                        .respond{request -> request.withPath(endpoint)
                                ? response().withStatusCode(OK_200.code()).withBody(json)
                                : HttpResponse.notFoundResponse()}
    }

    private static HttpRequest getXmlCacheRequest(String payload) {
        request().withMethod("POST")
                 .withPath(CACHE_ENDPOINT)
                 .withBody(jsonPath("\$.puts[?(@.value =~/^.*$payload.*\$/)]"))
    }
}
