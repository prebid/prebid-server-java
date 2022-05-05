package org.prebid.server.functional.testcontainers.scaffolding

import org.mockserver.model.HttpRequest
import org.prebid.server.functional.util.ObjectMapperWrapper
import org.testcontainers.containers.MockServerContainer

import static org.mockserver.model.HttpRequest.request

class PubStackAnalytics extends NetworkScaffolding {

    private static final String CONFIG_ENDPOINT = "/bootstrap"
    private static final String ANALYTICS_ENDPOINT = "/intake/auction"

    PubStackAnalytics(MockServerContainer mockServerContainer, ObjectMapperWrapper mapper) {
        super(mockServerContainer, CONFIG_ENDPOINT, mapper)
    }

    @Override
    protected HttpRequest getRequest() {
        request().withPath(ANALYTICS_ENDPOINT)
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
