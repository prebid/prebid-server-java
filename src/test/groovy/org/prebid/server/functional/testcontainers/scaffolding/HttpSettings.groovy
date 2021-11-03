package org.prebid.server.functional.testcontainers.scaffolding

import org.mockserver.model.HttpRequest
import org.prebid.server.functional.util.ObjectMapperWrapper
import org.testcontainers.containers.MockServerContainer

import static org.mockserver.model.HttpRequest.request

class HttpSettings extends NetworkScaffolding {

    private static final String ENDPOINT = "/stored-requests"
    private static final String AMP_ENDPOINT = "/amp-stored-requests"

    HttpSettings(MockServerContainer mockServerContainer, ObjectMapperWrapper mapper) {
        super(mockServerContainer, ENDPOINT, mapper)
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
}
