package org.prebid.server.functional.testcontainers.scaffolding

import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.mockserver.model.HttpRequest
import org.testcontainers.containers.MockServerContainer

import static org.mockserver.model.HttpRequest.request

class PubStackAnalytics extends NetworkScaffolding {

    private static final String CONFIG_ENDPOINT = "/bootstrap"
    private static final String ANALYTICS_ENDPOINT = "/intake/auction"

    PubStackAnalytics(MockServerContainer mockServerContainer) {
        super(mockServerContainer, CONFIG_ENDPOINT)
    }

    @Override
    protected HttpRequest getRequest() {
        request().withPath(ANALYTICS_ENDPOINT)
    }

    @Override
    protected RequestPatternBuilder getRequestPattern() {
        return null
    }

    @Override
    protected RequestPatternBuilder getRequestPattern(String value) {
        return null
    }

    @Override
    void setResponse() {

    }

    @Override
    protected HttpRequest getRequest(String value) {
        request().withPath(ANALYTICS_ENDPOINT)
    }

    @Override
    void reset() {
        super.reset(CONFIG_ENDPOINT)
        super.reset(ANALYTICS_ENDPOINT)
    }
}
