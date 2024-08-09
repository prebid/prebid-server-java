package org.prebid.server.it;

import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.prebid.server.model.Endpoint;
import org.prebid.server.util.IntegrationTestsUtil;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {"price-floors.enabled=true", "server.http.port=8050", "admin.port=0"})
public class PriceFloorsTest extends IntegrationTest {

    private static final int APP_PORT = 8050;

    private static final String PRICE_FLOORS = "Price Floors Test";
    private static final String FLOORS_FROM_REQUEST = "Floors from request";
    private static final String FLOORS_FROM_PROVIDER = "Floors from provider";

    private static final RequestSpecification SPEC = IntegrationTest.spec(APP_PORT);

    @Test
    public void openrtb2AuctionShouldApplyPriceFloorsForTheGenericBidder() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/floors-provider"))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/floors/provided-floors.json"))));

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/generic-exchange"))
                .inScenario(PRICE_FLOORS)
                .whenScenarioStateIs(STARTED)
                .withRequestBody(equalToJson(jsonFrom("openrtb2/floors/floors-test-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/floors/floors-test-bid-response.json")))
                .willSetStateTo(FLOORS_FROM_REQUEST));

        // when
        final Response firstResponse = IntegrationTestsUtil.responseFor(
                "openrtb2/floors/floors-test-auction-request-1.json",
                Endpoint.openrtb2_auction,
                SPEC);

        // then
        assertJsonEquals(
                "openrtb2/floors/floors-test-auction-response.json",
                firstResponse,
                singletonList("generic"));

        // given
        final StubMapping stubMapping = WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/generic-exchange"))
                .inScenario(PRICE_FLOORS)
                .whenScenarioStateIs(FLOORS_FROM_REQUEST)
                .withRequestBody(equalToJson(jsonFrom("openrtb2/floors/floors-test-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/floors/floors-test-bid-response.json")))
                .willSetStateTo(FLOORS_FROM_PROVIDER));

        // when
        final Response secondResponse = IntegrationTestsUtil.responseFor(
                "openrtb2/floors/floors-test-auction-request-2.json",
                Endpoint.openrtb2_auction,
                SPEC);

        // then
        assertThat(stubMapping.getNewScenarioState()).isEqualTo(FLOORS_FROM_PROVIDER);
        assertJsonEquals(
                "openrtb2/floors/floors-test-auction-response.json",
                secondResponse,
                singletonList("generic"));
    }

    @Test
    public void openrtb2AuctionShouldSkipPriceFloorsForTheGenericBidderWhenGenericIsInNoSignalBiddersList()
            throws IOException, JSONException {

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/generic-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/floors/floors-test-bid-request-no-signal.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/floors/floors-test-bid-response.json"))));

        // when
        final Response firstResponse = IntegrationTestsUtil.responseFor(
                "openrtb2/floors/floors-test-auction-request-no-signal.json",
                Endpoint.openrtb2_auction,
                SPEC);

        // then
        assertJsonEquals(
                "openrtb2/floors/floors-test-auction-response-no-signal.json",
                firstResponse,
                singletonList("generic"));
    }
}
