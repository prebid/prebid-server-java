package org.prebid.server.functional.testcontainers.scaffolding

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.matching.RequestPattern
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.prebid.server.functional.model.HttpStatusCode
import org.prebid.server.functional.model.ResponseModel
import org.prebid.server.functional.testcontainers.container.NetworkServiceContainer
import org.prebid.server.functional.util.ObjectMapperWrapper

import static org.prebid.server.functional.model.HttpStatusCode.OK_200

abstract class NetworkScaffolding implements ObjectMapperWrapper {

    protected WireMock wireMockClient
    protected String endpoint

    NetworkScaffolding(NetworkServiceContainer wireMockContainer, String endpoint) {
        this.wireMockClient = new WireMock(wireMockContainer.host, wireMockContainer.firstMappedPort)
        this.endpoint = endpoint
    }

    abstract protected RequestPattern getRequest()

    abstract protected RequestPatternBuilder getRequest(String value)

    abstract void setResponse()

    int getRequestCount(RequestPatternBuilder requestPatternBuilder) {
        return wireMockClient.find(requestPatternBuilder).size()
    }

    int getRequestCount(String value) {
        return wireMockClient.find(getRequest(value)).size()
    }

    void setResponse(RequestPattern requestPattern,
                     ResponseModel responseModel,
                     HttpStatusCode statusCode = OK_200) {

        def responseBuilder = ResponseDefinitionBuilder.responseDefinition()
                .withStatus(statusCode.code)
                .withHeader("Content-Type", "application/json")
                .withBody(encode(responseModel))

        wireMockClient.register(new StubMapping(requestPattern, responseBuilder.build()))
    }

    void setResponse(String value,
                     ResponseModel responseModel,
                     Map<String, String> headers) {
        setResponse(value, responseModel, OK_200, headers)
    }

    void setResponse(String value,
                     ResponseModel responseModel,
                     HttpStatusCode statusCode = OK_200,
                     Map<String, String> headers = [:]) {

        def responseBuilder = ResponseDefinitionBuilder.responseDefinition()
                .withStatus(statusCode.code)
                .withHeader("Content-Type", "application/json")
                .withBody(encode(responseModel))

        headers.each { k, v ->
            responseBuilder.withHeader(k, v)
        }

        wireMockClient.register(new StubMapping(getRequest(value).build(), responseBuilder.build()))
    }

    void setResponse(String value,
                     ResponseModel responseModel,
                     int responseDelay,
                     HttpStatusCode statusCode = OK_200) {
        def responseBuilder = ResponseDefinitionBuilder.responseDefinition()
                .withStatus(statusCode.code)
                .withHeader("Content-Type", "application/json")
                .withBody(encode(responseModel))
                .withFixedDelay(responseDelay)

        wireMockClient.register(new StubMapping(getRequest(value).build(), responseBuilder.build()))
    }

    void setResponse(String value, String mockResponse) {
        def responseBuilder = ResponseDefinitionBuilder.responseDefinition()
                .withStatus(OK_200.code)
                .withBody(mockResponse)

        wireMockClient.register(new StubMapping(getRequest(value).build(), responseBuilder.build()))
    }

    void setResponse(ResponseModel responseModel) {
        def responseBuilder = ResponseDefinitionBuilder.responseDefinition()
                .withStatus(OK_200.code)
                .withHeader("Content-Type", "application/json")
                .withBody(encode(responseModel))

        wireMockClient.register(new StubMapping(getRequest(), responseBuilder.build()))
    }

    void setResponse(String value, HttpStatusCode httpStatusCode) {
        def responseBuilder = ResponseDefinitionBuilder.responseDefinition()
                .withStatus(httpStatusCode.code)

        wireMockClient.register(new StubMapping(getRequest(value).build(), responseBuilder.build()))
    }

    void setResponseWithTimeout(String value, int timeoutSec = 5) {
        def responseBuilder = ResponseDefinitionBuilder.responseDefinition()
                .withFixedDelay(timeoutSec * 1000)

        wireMockClient.register(new StubMapping(getRequest(value).build(), responseBuilder.build()))
    }

    List<String> getRecordedRequestsBody(RequestPatternBuilder requestPatternBuilder) {
        wireMockClient.find(requestPatternBuilder).bodyAsString
    }

    String getRecordedRequestsQueryParameters(RequestPatternBuilder requestPatternBuilder) {
        wireMockClient.find(requestPatternBuilder)
                .collect {
                    it.queryParams.collectEntries { k, v ->
                        [(k): v.values()]
                    }.toString()
                }
    }

    List<String> getRecordedRequestsBody(String value) {
        wireMockClient.find(getRequest(value)).bodyAsString
    }

    Map<String, List<String>> getLastRecordedRequestHeaders(RequestPatternBuilder requestPatternBuilder) {
        getRecordedRequestsHeaders(requestPatternBuilder).last()
    }

    List<Map<String, List<String>>> getRecordedRequestsHeaders(RequestPatternBuilder requestPatternBuilder) {
        def requests = wireMockClient.find(requestPatternBuilder)

        List<Map<String, List<String>>> result = []
        requests.each { req ->
            Map<String, List<String>> headersMap = [:]
            req.headers.all().each { header ->
                headersMap[header.key() as String] = header.values()*.toString()
            }
            result << headersMap
        }

        result
    }

    Map<String, List<String>> getLastRecordedRequestHeaders(String value) {
        getRecordedRequestsHeaders(value).last()
    }

    List<Map<String, List<String>>> getRecordedRequestsHeaders(String value) {
        getRecordedRequestsHeaders(getRequest(value))
    }

    void reset() {
        wireMockClient.resetMappings()
        wireMockClient.resetScenarios()
        wireMockClient.resetRequests()
    }
}
