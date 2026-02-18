package org.prebid.server.functional.testcontainers.scaffolding

import com.github.tomakehurst.wiremock.matching.RequestPattern
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.prebid.server.functional.model.mock.services.currencyconversion.CurrencyConversionRatesResponse
import org.prebid.server.functional.testcontainers.container.NetworkServiceContainer

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response
import static org.mockserver.model.HttpStatusCode.OK_200
import static org.prebid.server.functional.util.CurrencyUtil.DEFAULT_CURRENCY_RATES

class CurrencyConversion extends NetworkScaffolding {

    static final String CURRENCY_ENDPOINT_PATH = "/currency"
    private static final CurrencyConversionRatesResponse DEFAULT_RATES_RESPONSE = CurrencyConversionRatesResponse.getDefaultCurrencyConversionRatesResponse(DEFAULT_CURRENCY_RATES)

    CurrencyConversion(NetworkServiceContainer mockServerContainer) {
        super(mockServerContainer, CURRENCY_ENDPOINT_PATH)
    }

    void setCurrencyConversionRatesResponse(CurrencyConversionRatesResponse conversionRatesResponse = DEFAULT_RATES_RESPONSE) {
        setResponse(getRequest(), conversionRatesResponse)
    }

    @Override
    void setResponse() {
        mockServerClient.when(request().withPath(endpoint))
                .respond(response().withStatusCode(OK_200.code()))
    }

    @Override
    protected RequestPattern getRequest() {
        getRequestedFor(urlEqualTo(CURRENCY_ENDPOINT_PATH))
                .build()
    }

    @Override
    protected RequestPatternBuilder getRequest(String value) {
        return null
    }
}
