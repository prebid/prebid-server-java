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

public class PgamSspTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromPgamSsp() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/pgamssp-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/pgamssp/test-pgamssp-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/pgamssp/test-pgamssp-bid-response.json"))));

        // when
        final Response response = responseFor(
                "openrtb2/pgamssp/test-auction-pgamssp-request.json",
                Endpoint.openrtb2_auction
        );

        // then
        assertJsonEquals("openrtb2/pgamssp/test-auction-pgamssp-response.json", response, List.of("pgamssp"));
    }

}
