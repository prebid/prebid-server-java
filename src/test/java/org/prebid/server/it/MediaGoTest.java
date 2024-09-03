package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.prebid.server.model.Endpoint;

import java.io.IOException;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

public class MediaGoTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromMediago() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/mediago-exchange"))
                .withQueryParam("token", equalTo("request_token"))
                .withQueryParam("region", equalTo("jp"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/mediago/test-mediago-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/mediago/test-mediago-bid-response.json"))));

        // when
        final Response response = responseFor(
                "openrtb2/mediago/test-auction-mediago-request.json",
                Endpoint.openrtb2_auction
        );

        // then
        assertJsonEquals("openrtb2/mediago/test-auction-mediago-response.json", response, List.of("mediago"));
    }

}
