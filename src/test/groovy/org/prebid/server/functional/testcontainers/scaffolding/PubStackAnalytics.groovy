package org.prebid.server.functional.testcontainers.scaffolding

import com.github.tomakehurst.wiremock.matching.RequestPattern
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.prebid.server.functional.model.mock.services.pubstack.PubStackResponse
import org.prebid.server.functional.testcontainers.container.NetworkServiceContainer

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse
import static com.github.tomakehurst.wiremock.client.WireMock.get
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import static org.apache.http.HttpStatus.SC_OK

class PubStackAnalytics extends NetworkScaffolding {

    private static final String CONFIG_ENDPOINT = "/bootstrap"
    private static final String ANALYTICS_ENDPOINT = "/intake/auction"

    PubStackAnalytics(NetworkServiceContainer wireMockContainer) {
        super(wireMockContainer, CONFIG_ENDPOINT)
    }

    @Override
    protected RequestPattern getRequest() {
        postRequestedFor(urlEqualTo(ANALYTICS_ENDPOINT))
                .build()
    }

    @Override
    protected RequestPatternBuilder getRequest(String value) {
        throw new UnsupportedOperationException()
    }

    @Override
    void setResponse() {
        throw new UnsupportedOperationException()
    }

    void setResponse(PubStackResponse pubStackResponse) {
        wireMockClient.register(get(urlPathEqualTo(endpoint))
                .atPriority(Integer.MAX_VALUE)
                .willReturn(aResponse()
                        .withStatus(SC_OK)
                        .withTransformers("response-template")
                .withBody(encode(pubStackResponse))
        ))

    }
}
