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

public class NextMillenniumTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromNextMillennium() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/nextmillennium-exchange"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/nextmillennium/test-nextmillennium-bid-request.json"),
                        true,
                        true))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/nextmillennium/test-nextmillennium-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/nextmillennium/test-auction-nextmillennium-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/nextmillennium/test-auction-nextmillennium-response.json", response,
                singletonList("nextmillennium"));
    }
}
