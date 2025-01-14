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

public class OwnAdxTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromOwnAdx() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/ownadx-exchange/bid/testSeatId/testSspId"))
                .withQueryParam("token", equalTo("testTokenId"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/ownadx/test-ownadx-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/ownadx/test-ownadx-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/ownadx/test-auction-ownadx-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/ownadx/test-auction-ownadx-response.json", response, singletonList("ownadx"));
    }
}
