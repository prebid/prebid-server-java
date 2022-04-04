package org.prebid.server.it;

import com.github.tomakehurst.wiremock.stubbing.StubMapping;
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
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@TestPropertySource(properties = {"price-floors.enabled=true"})
public class PriceFloorsTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldApplyPriceFloorsForTheGenericBidder() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/floors-provider"))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/floors/provided-floors.json"))));

        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/generic-exchange"))
                .inScenario("Price Floors Test")
                .whenScenarioStateIs(STARTED)
                .withRequestBody(equalToJson(jsonFrom("openrtb2/floors/floors-test-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/floors/floors-test-bid-response.json")))
                .willSetStateTo("Floors from request"));

        // when
        final Response firstResponse = responseFor("openrtb2/floors/floors-test-auction-request-1.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/floors/floors-test-auction-response.json", firstResponse, singletonList("generic"));

        // given
        final StubMapping stubMapping = WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/generic-exchange"))
                .inScenario("Price Floors Test")
                .whenScenarioStateIs("Floors from request")
                .withRequestBody(equalToJson(jsonFrom("openrtb2/floors/floors-test-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/floors/floors-test-bid-response.json")))
                .willSetStateTo("Floors from provider"));

        // when
        final Response secondResponse = responseFor("openrtb2/floors/floors-test-auction-request-2.json",
                Endpoint.openrtb2_auction);

        // then
        assertThat(stubMapping.getNewScenarioState()).isEqualTo("Floors from provider");
        assertJsonEquals(
                "openrtb2/floors/floors-test-auction-response.json", secondResponse, singletonList("generic"));
    }
}
