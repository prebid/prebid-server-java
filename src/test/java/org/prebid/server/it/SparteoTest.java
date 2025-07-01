package org.prebid.server.it;

import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.prebid.server.model.Endpoint;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

public class SparteoTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromSparteoBanner() throws Exception {
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/sparteo-exchange"))
                .withRequestBody(equalToJson(
                    jsonFrom("openrtb2/sparteo/test-sparteo-bid-request.json")))
                .willReturn(aResponse().withBody(
                    jsonFrom("openrtb2/sparteo/test-sparteo-bid-response.json"))));

        final Response response = responseFor(
                "openrtb2/sparteo/test-auction-sparteo-request.json",
                Endpoint.openrtb2_auction);

        assertJsonEquals(
                "openrtb2/sparteo/test-auction-sparteo-response.json",
                response,
                singletonList("sparteo"));
    }
}
