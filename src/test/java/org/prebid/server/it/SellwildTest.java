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

public class SellwildTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromSellwild() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/sellwild-exchange/test-partner"))
                .withHeader("x-openrtb-version", equalTo("2.6"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/sellwild/test-sellwild-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/sellwild/test-sellwild-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/sellwild/test-auction-sellwild-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/sellwild/test-auction-sellwild-response.json", response,
                singletonList("sellwild"));
    }
}
