package org.prebid.server.functional.testcontainers.scaffolding

import com.github.tomakehurst.wiremock.matching.RequestPattern
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.mockserver.model.HttpStatusCode
import org.prebid.server.functional.model.pricefloors.PriceFloorData
import org.prebid.server.functional.testcontainers.container.NetworkServiceContainer

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse
import static com.github.tomakehurst.wiremock.client.WireMock.any
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import static org.mockserver.model.HttpStatusCode.OK_200

class FloorsProvider extends NetworkScaffolding {

    public static final String FLOORS_ENDPOINT = "/floors-provider/"

    FloorsProvider(NetworkServiceContainer wireMockContainer) {
        super(wireMockContainer, FLOORS_ENDPOINT)
    }

    protected RequestPattern getRequest() {
        anyRequestedFor(urlEqualTo(FLOORS_ENDPOINT))
                .build()
    }

    @Override
    protected RequestPatternBuilder getRequest(String accountId) {
        getRequestedFor(urlEqualTo(FLOORS_ENDPOINT + accountId))
    }

    @Override
    void setResponse() {
        wireMockClient.register(any(urlMatching("^.*$endpoint.*\$"))
                .atPriority(Integer.MAX_VALUE)
                .willReturn(aResponse()
                        .withStatus(OK_200.code())
                        .withBody(encode(PriceFloorData.priceFloorData))))
    }
}
