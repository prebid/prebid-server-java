package org.prebid.server.functional.testcontainers.scaffolding

import org.mockserver.matchers.Times
import org.mockserver.model.Header
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpStatusCode
import org.prebid.server.functional.model.ResponseModel
import org.testcontainers.containers.MockServerContainer

import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response
import static org.mockserver.model.HttpStatusCode.OK_200
import static org.mockserver.model.MediaType.APPLICATION_JSON

class HttpSettings extends NetworkScaffolding {

    private static final String ENDPOINT = "/stored-requests"
    private static final String RFC_ENDPOINT = "/stored-requests-rfc"
    private static final String AMP_ENDPOINT = "/amp-stored-requests"

    HttpSettings(MockServerContainer mockServerContainer) {
        super(mockServerContainer, ENDPOINT)
    }

    @Override
    protected HttpRequest getRequest(String accountId) {
        request().withPath(ENDPOINT)
                 .withQueryStringParameter("account-ids", "[\"$accountId\"]")
    }

    @Override
    protected HttpRequest getRequest() {
        request().withPath(ENDPOINT)
    }

    @Override
    void setResponse() {
    }

    protected HttpRequest getRfcRequest(String accountId) {
        request().withPath(RFC_ENDPOINT)
                .withQueryStringParameter("account-id", accountId)
    }


    void setRfcResponse(String value,
                     ResponseModel responseModel,
                     HttpStatusCode statusCode = OK_200,
                     Map<String, String> headers = [:]) {
        def responseHeaders = headers.collect { new Header(it.key, it.value) }
        def mockResponse = encode(responseModel)
        mockServerClient.when(getRfcRequest(value), Times.unlimited())
                .respond(response().withStatusCode(statusCode.code())
                        .withBody(mockResponse, APPLICATION_JSON)
                        .withHeaders(responseHeaders))
    }

    int getRfcRequestCount(String value) {
        mockServerClient.retrieveRecordedRequests(getRfcRequest(value))
                .size()
    }

    @Override
    void reset() {
        super.reset(ENDPOINT)
        super.reset(RFC_ENDPOINT)
        super.reset(AMP_ENDPOINT)
    }

    static String getEndpoint() {
        return ENDPOINT
    }

    static String getAmpEndpoint() {
        return AMP_ENDPOINT
    }

    static String getRfcEndpoint() {
        return RFC_ENDPOINT
    }
}
