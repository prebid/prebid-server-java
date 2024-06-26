package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.prebid.server.model.Endpoint;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

public class BraveTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromBrave() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/brave-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/brave/test-brave-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/brave/test-brave-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/brave/test-auction-brave-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/brave/test-auction-brave-response.json", response, singletonList("brave"));
    }
}
