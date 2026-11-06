package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.prebid.server.model.Endpoint;

import java.io.IOException;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

public class Peak226Test extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromPeak226() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/peak226-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/peak226/test-peak226-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/peak226/test-peak226-bid-response.json"))));

        // when
        final Response response = responseFor(
                "openrtb2/peak226/test-auction-peak226-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/peak226/test-auction-peak226-response.json", response, List.of("peak226"));
    }
}
