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

public class ConsumableTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromConsumable() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/consumable-exchange/sb/rtb"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/consumable/test-consumable-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/consumable/test-consumable-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/consumable/test-auction-consumable-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/consumable/test-auction-consumable-response.json", response,
                singletonList("consumable"));
    }
}
