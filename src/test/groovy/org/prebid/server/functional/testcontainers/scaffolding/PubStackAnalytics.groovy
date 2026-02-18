package org.prebid.server.functional.testcontainers.scaffolding

import com.github.tomakehurst.wiremock.matching.RequestPattern
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.prebid.server.functional.testcontainers.container.NetworkServiceContainer

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo

class PubStackAnalytics extends NetworkScaffolding {

    private static final String CONFIG_ENDPOINT = "/bootstrap"
    private static final String ANALYTICS_ENDPOINT = "/intake/auction"

    PubStackAnalytics(NetworkServiceContainer mockServerContainer) {
        super(mockServerContainer, CONFIG_ENDPOINT)
    }

    @Override
    protected RequestPattern getRequest() {
        postRequestedFor(urlEqualTo(ANALYTICS_ENDPOINT))
                .build()
    }

    @Override
    protected RequestPatternBuilder getRequest(String value) {
        return null
    }

    @Override
    void setResponse() {}
}
