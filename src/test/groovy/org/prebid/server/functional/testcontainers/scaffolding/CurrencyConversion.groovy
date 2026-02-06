package org.prebid.server.functional.testcontainers.scaffolding

import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.mockserver.model.HttpRequest
import org.prebid.server.functional.model.mock.services.currencyconversion.CurrencyConversionRatesResponse
import org.testcontainers.containers.MockServerContainer

import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response
import static org.mockserver.model.HttpStatusCode.OK_200
import static org.prebid.server.functional.util.CurrencyUtil.DEFAULT_CURRENCY_RATES

class CurrencyConversion extends NetworkScaffolding {

    static final String CURRENCY_ENDPOINT_PATH = "/currency"
    private static final CurrencyConversionRatesResponse DEFAULT_RATES_RESPONSE = CurrencyConversionRatesResponse.getDefaultCurrencyConversionRatesResponse(DEFAULT_CURRENCY_RATES)

    CurrencyConversion(MockServerContainer mockServerContainer) {
        super(mockServerContainer, CURRENCY_ENDPOINT_PATH)
    }

    void setCurrencyConversionRatesResponse(CurrencyConversionRatesResponse conversionRatesResponse = DEFAULT_RATES_RESPONSE) {
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

    @Override
    protected RequestPatternBuilder getRequestPattern() {
        return null
    }

    @Override
    protected RequestPatternBuilder getRequestPattern(String value) {
        return null
    }
}
