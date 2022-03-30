package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
@TestPropertySource("floors-application.properties")
public class PriceFloorsTest extends IntegrationTest {

    @Test
    public void auctionFloorsTest() throws IOException, JSONException {
        // for both tests
        // given
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/floors-provider"))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/floors/provided-floors.json"))));

        // first run
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/generic-exchange"))
                .inScenario("Price Floors Test")
                .whenScenarioStateIs(STARTED)
                .withRequestBody(equalToJson(jsonFrom("openrtb2/floors/floors-test-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/floors/floors-test-bid-response.json")))
                .willSetStateTo("Run 1 passed"));

        // when
        final Response firstResponse = responseFor("openrtb2/floors/floors-test-auction-request-1.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/floors/floors-test-auction-response.json", firstResponse, singletonList("generic"));

        // second run
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/generic-exchange"))
                .inScenario("Price Floors Test")
                .whenScenarioStateIs("Run 1 passed")
                .withRequestBody(equalToJson(jsonFrom("openrtb2/floors/floors-test-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/floors/floors-test-bid-response.json")))
                .willSetStateTo("Run 2 passed"));

        // when
        final Response secondResponse = responseFor("openrtb2/floors/floors-test-auction-request-2.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/floors/floors-test-auction-response.json", secondResponse, singletonList("generic"));
    }
}
