package org.prebid.server.functional.testcontainers.scaffolding

import org.mockserver.model.HttpRequest
import org.prebid.server.functional.model.mock.services.currencyconversion.CurrencyConversionRatesResponse
import org.prebid.server.functional.util.ObjectMapperWrapper
import org.testcontainers.containers.MockServerContainer

import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response
import static org.mockserver.model.HttpStatusCode.OK_200

class CurrencyConversion extends NetworkScaffolding {

    static final String CURRENCY_ENDPOINT_PATH = "/currency"

    CurrencyConversion(MockServerContainer mockServerContainer, ObjectMapperWrapper mapper) {
        super(mockServerContainer, CURRENCY_ENDPOINT_PATH, mapper)
    }

    void setCurrencyConversionRatesResponse(CurrencyConversionRatesResponse conversionRatesResponse) {
        setResponse(request, conversionRatesResponse)
    }

    @Override
    void setResponse() {
        mockServerClient.when(request().withPath(endpoint))
                        .respond(response().withStatusCode(OK_200.code()))
    }

    @Override
    protected HttpRequest getRequest(String ignored) {
        request().withMethod("GET")
                 .withPath(CURRENCY_ENDPOINT_PATH)
    }

    @Override
    protected HttpRequest getRequest() {
        request().withMethod("GET")
                 .withPath(CURRENCY_ENDPOINT_PATH)
    }
}
