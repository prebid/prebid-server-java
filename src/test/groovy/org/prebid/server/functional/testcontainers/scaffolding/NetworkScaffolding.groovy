package org.prebid.server.functional.testcontainers.scaffolding

import org.mockserver.client.MockServerClient
import org.mockserver.matchers.Times
import org.mockserver.model.ClearType
import org.mockserver.model.Header
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpStatusCode
import org.prebid.server.functional.model.ResponseModel
import org.prebid.server.functional.util.ObjectMapperWrapper
import org.testcontainers.containers.MockServerContainer

import static java.util.concurrent.TimeUnit.SECONDS
import static org.mockserver.model.ClearType.EXPECTATIONS
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

    void setResponse(HttpRequest httpRequest,
                     ResponseModel responseModel,
                     HttpStatusCode statusCode = OK_200,
                     Times times = Times.exactly(1)) {
        def mockResponse = mapper.encode(responseModel)
        mockServerClient.when(httpRequest, times)
                        .respond(response().withStatusCode(statusCode.code())
                                           .withBody(mockResponse, APPLICATION_JSON))
    }

    void setResponse(String value, ResponseModel responseModel, Map<String, String> headers = [:]) {
        def responseHeaders = headers.collect { new Header(it.key, it.value) }
        def mockResponse = mapper.encode(responseModel)
        mockServerClient.when(getRequest(value), Times.unlimited())
                        .respond(response().withStatusCode(OK_200.code())
                                           .withBody(mockResponse, APPLICATION_JSON)
                                           .withHeaders(responseHeaders))
    }

    void setResponse(String value, String mockResponse) {
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

    void setResponse(String value, HttpStatusCode httpStatusCode) {
        mockServerClient.when(getRequest(value), Times.exactly(1))
                        .respond(response().withStatusCode(httpStatusCode.code()))
    }

    void setResponse(String value, HttpStatusCode httpStatusCode, String errorText) {
        mockServerClient.when(getRequest(value), Times.exactly(1))
                        .respond(response().withStatusCode(httpStatusCode.code())
                                           .withBody(errorText, APPLICATION_JSON))
    }

    void setResponseWithTimeout(String value, int timeoutSec = 5) {
        mockServerClient.when(getRequest(value), Times.exactly(1))
                        .respond(response().withDelay(SECONDS, timeoutSec))
    }

    protected def getRequestAndResponse() {
        mockServerClient.retrieveRecordedRequestsAndResponses(request())
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

    void reset(String resetEndpoint = endpoint, ClearType clearType = EXPECTATIONS) {
        mockServerClient.clear(request().withPath(resetEndpoint), clearType)
    }

    private static List<Map<String, String>> getRequestsHeaders(List<HttpRequest> httpRequests) {
        httpRequests*.headers*.entries*.collectEntries { header ->
            [header.name as String, header.values.collect { it as String }]
        }
    }
}
