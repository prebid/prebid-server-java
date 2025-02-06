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

public class MeloZenTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromMelozen() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/melozen-exchange"))
                .withQueryParam("pubId", equalTo("publisherId"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/melozen/test-melozen-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/melozen/test-melozen-bid-response.json"))));

        // when
        final Response response = responseFor(
                "openrtb2/melozen/test-auction-melozen-request.json",
                Endpoint.openrtb2_auction
        );

        // then
        assertJsonEquals("openrtb2/melozen/test-auction-melozen-response.json", response, List.of("melozen"));
    }

}
