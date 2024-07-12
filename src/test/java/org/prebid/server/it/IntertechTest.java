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

public class IntertechTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromIntertech() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(
                urlPathEqualTo("/intertech-exchange&target-ref=http%3A%2F%2Fwww.example.com&ssp-cur=USD"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/intertech/test-intertech-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/intertech/test-intertech-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/intertech/test-auction-intertech-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/intertech/test-auction-intertech-response.json", response,
                singletonList("intertech"));
    }
}
