package org.prebid.server.functional.testcontainers.scaffolding

import org.mockserver.matchers.TimeToLive
import org.mockserver.matchers.Times
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.prebid.server.functional.model.mock.services.floorsprovider.PriceFloorRules
import org.prebid.server.functional.util.ObjectMapperWrapper
import org.testcontainers.containers.MockServerContainer

import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response
import static org.mockserver.model.HttpStatusCode.OK_200

class FloorsProvider extends NetworkScaffolding {

    public static final String FLOORS_ENDPOINT = "/floors-provider/"

    FloorsProvider(MockServerContainer mockServerContainer, ObjectMapperWrapper mapper) {
        super(mockServerContainer, FLOORS_ENDPOINT, mapper)
    }

    @Override
    protected HttpRequest getRequest(String accountId) {
        request().withPath(FLOORS_ENDPOINT + accountId)
    }

    @Override
    protected HttpRequest getRequest() {
        request().withPath(FLOORS_ENDPOINT)
    }

    @Override
    void setResponse() {
        mockServerClient.when(request().withPath("^.*$endpoint.*\$"), Times.unlimited(), TimeToLive.unlimited(), -10)
                        .respond{request -> request.withPath(endpoint)
                                ? response().withStatusCode(OK_200.code()).withBody(defaultResponse)
                                : HttpResponse.notFoundResponse()}
    }

    private String getDefaultResponse() {
        mapper.encode(PriceFloorRules.priceFloorRules)
    }
}
