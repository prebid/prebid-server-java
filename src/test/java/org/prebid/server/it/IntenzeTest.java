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

public class IntenzeTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromIntenze() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/intenze-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/intenze/test-intenze-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/intenze/test-intenze-bid-response.json"))));

        // when
        final Response response = responseFor(
                "openrtb2/intenze/test-auction-intenze-request.json",
                Endpoint.openrtb2_auction
        );

        // then
        assertJsonEquals("openrtb2/intenze/test-auction-intenze-response.json", response, List.of("intenze"));
    }

}
