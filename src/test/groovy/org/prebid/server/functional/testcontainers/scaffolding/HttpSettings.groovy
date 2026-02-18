package org.prebid.server.functional.testcontainers.scaffolding

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.matching.RequestPattern
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.prebid.server.functional.model.HttpStatusCode
import org.prebid.server.functional.model.ResponseModel
import org.prebid.server.functional.testcontainers.container.NetworkServiceContainer

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import static org.prebid.server.functional.model.HttpStatusCode.OK_200

class HttpSettings extends NetworkScaffolding {

    private static final String ENDPOINT = "/stored-requests"
    private static final String RFC_ENDPOINT = "/stored-requests-rfc"
    private static final String AMP_ENDPOINT = "/amp-stored-requests"

    HttpSettings(NetworkServiceContainer wireMockContainer, String endpoint = ENDPOINT) {
        super(wireMockContainer, endpoint)
    }

    protected RequestPattern getRequest() {
        anyRequestedFor(urlPathEqualTo(endpoint)).build()
    }

    @Override
    protected RequestPatternBuilder getRequest(String accountId) {
        getRequestedFor(urlPathEqualTo(endpoint))
                .withQueryParam("account-ids", equalTo("[\"" + accountId + "\"]"))
    }

    @Override
    void setResponse() {
    }

    protected RequestPatternBuilder getRfcRequestPattern(String accountId) {
        anyRequestedFor(urlPathEqualTo(endpoint))
                .withQueryParam("account-id", equalTo(accountId))
    }

    void setRfcResponse(String value,
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

        wireMockClient.register(new StubMapping(getRfcRequestPattern(value).build(), responseBuilder.build()))
    }

    int getRfcRequestCount(String value) {
        return wireMockClient.find(getRfcRequestPattern(value)).size()
    }
}
