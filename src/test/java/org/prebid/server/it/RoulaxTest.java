package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.prebid.server.model.Endpoint;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

public class RoulaxTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromTheRoulaxBidder() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/roulax-exchange/testPublisherPath"))
                        .withQueryParam("pid", equalTo("testPid"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/roulax/test-roulax-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/roulax/test-roulax-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/roulax/test-auction-roulax-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/roulax/test-auction-roulax-response.json", response,
                singletonList("roulax"));
    }
}
