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

public class AdverxoTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromTheAdverxo() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/adverxo-exchange"))
                .withQueryParam("adUnitId", equalTo("1"))
                .withQueryParam("auth", equalTo("123456"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/adverxo/test-adverxo-bid-request.json"), true, true))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/adverxo/test-adverxo-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/adverxo/test-auction-adverxo-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/adverxo/test-auction-adverxo-response.json", response,
                singletonList("adverxo"));
    }
}
