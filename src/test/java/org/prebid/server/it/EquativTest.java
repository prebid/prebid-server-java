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

public class EquativTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromEquativ() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/smartadserver-exchange/api/bid"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/equativ/test-equativ-bid-request.json")))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/equativ/test-equativ-bid-response.json"))));

        // when
        final Response response = responseFor(
                "openrtb2/equativ/test-auction-equativ-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/equativ/test-auction-equativ-response.json", response,
                singletonList("equativ"));
    }
}
