package org.prebid.server.functional.testcontainers.scaffolding

import org.mockserver.client.MockServerClient
import org.mockserver.matchers.Times
import org.mockserver.model.HttpRequest
import org.prebid.server.functional.model.ResponseModel
import org.prebid.server.functional.util.ObjectMapperWrapper
import org.testcontainers.containers.MockServerContainer

import static java.util.concurrent.TimeUnit.SECONDS
import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response
import static org.mockserver.model.HttpStatusCode.OK_200
import static org.mockserver.model.MediaType.APPLICATION_JSON

abstract class NetworkScaffolding {

    protected MockServerClient mockServerClient
    protected String endpoint
    protected ObjectMapperWrapper mapper

    NetworkScaffolding(MockServerContainer mockServerContainer, String endpoint, ObjectMapperWrapper mapper) {
        this.mockServerClient = new MockServerClient(mockServerContainer.host, mockServerContainer.serverPort)
        this.endpoint = endpoint
        this.mapper = mapper
    }

    abstract protected HttpRequest getRequest(String value)

    abstract protected HttpRequest getRequest()

    abstract void setResponse()

    int getRequestCount(HttpRequest httpRequest) {
        mockServerClient.retrieveRecordedRequests(httpRequest)
                        .size()
    }

    int getRequestCount(String value) {
        mockServerClient.retrieveRecordedRequests(getRequest(value))
                        .size()
    }

    int getRequestCount() {
        mockServerClient.retrieveRecordedRequests(request)
                        .size()
    }

    boolean checkRequestCount(int expectedCount, int pollTime = 1000, int pollFrequency = 50) {
        def expectedCountReached = false
        def startTime = System.currentTimeMillis()
        def elapsedTime = 0

        while (elapsedTime < pollTime) {
            def requestCount = getRequestCount()
            if (requestCount == expectedCount) {
                expectedCountReached = true
                break
            } else if (requestCount > expectedCount) {
                throw new IllegalStateException("The number of recorded requests: $requestCount exceeds the expected number: $expectedCount")
            } else {
                elapsedTime += System.currentTimeMillis() - startTime
                Thread.sleep(pollFrequency)
            }
        }

        expectedCountReached
    }

    void setResponse(HttpRequest httpRequest, ResponseModel responseModel) {
        def mockResponse = mapper.encode(responseModel)
        mockServerClient.when(httpRequest, Times.exactly(1))
                        .respond(response().withStatusCode(OK_200.code())
                                           .withBody(mockResponse, APPLICATION_JSON))
    }

    void setResponse(String value, ResponseModel responseModel) {
        def mockResponse = mapper.encode(responseModel)
        mockServerClient.when(getRequest(value), Times.exactly(1))
                        .respond(response().withStatusCode(OK_200.code())
                                           .withBody(mockResponse, APPLICATION_JSON))
    }

    void setResponse(ResponseModel responseModel) {
        def mockResponse = mapper.encode(responseModel)
        mockServerClient.when(request().withPath(endpoint))
                        .respond(response().withStatusCode(OK_200.code())
                                           .withBody(mockResponse, APPLICATION_JSON))
    }

    void setResponse(String value, int httpStatusCode) {
        mockServerClient.when(getRequest(value), Times.exactly(1))
                        .respond(response().withStatusCode(httpStatusCode))
    }

    void setResponse(String value, int httpStatusCode, String errorText) {
        mockServerClient.when(getRequest(value), Times.exactly(1))
                        .respond(response().withStatusCode(httpStatusCode)
                                           .withBody(errorText, APPLICATION_JSON))
    }

    void setResponseWithTimeout(String value) {
        mockServerClient.when(getRequest(value), Times.exactly(1))
                        .respond(response().withDelay(SECONDS, 5))
    }

    List<String> getRecordedRequestsBody(HttpRequest httpRequest) {
        mockServerClient.retrieveRecordedRequests(httpRequest)
                        .collect { it.body.toString() }
    }

    List<String> getRecordedRequestsBody(String value) {
        mockServerClient.retrieveRecordedRequests(getRequest(value))
                        .collect { it.body.toString() }
    }

    List<String> getRecordedRequestsBody() {
        mockServerClient.retrieveRecordedRequests(request)
                        .collect { it.body.toString() }
    }

    Map<String, String> getLastRecordedRequestHeaders(HttpRequest httpRequest) {
        getRecordedRequestsHeaders(httpRequest).last()
    }

    List<Map<String, String>> getRecordedRequestsHeaders(HttpRequest httpRequest) {
        getRequestsHeaders(mockServerClient.retrieveRecordedRequests(httpRequest) as List<HttpRequest>)
    }

    Map<String, String> getLastRecordedRequestHeaders(String value) {
        getRecordedRequestsHeaders(value).last()
    }

    List<Map<String, String>> getRecordedRequestsHeaders(String value) {
        getRequestsHeaders(mockServerClient.retrieveRecordedRequests(getRequest(value)) as List<HttpRequest>)
    }

    void reset() {
        mockServerClient.clear(request().withPath(endpoint))
    }

    private static List<Map<String, String>> getRequestsHeaders(List<HttpRequest> httpRequests) {
        httpRequests*.headers*.entries*.collectEntries { header ->
            [header.name as String, header.values.collect { it as String }]
        }
    }
}
