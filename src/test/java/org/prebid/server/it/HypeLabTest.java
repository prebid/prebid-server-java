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

public class HypeLabTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromHypeLab() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/hypelab-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/hypelab/test-hypelab-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/hypelab/test-hypelab-bid-response.json"))));

        // when
        final Response response = responseFor(
                "openrtb2/hypelab/test-auction-hypelab-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/hypelab/test-auction-hypelab-response.json", response,
                singletonList("hypelab"));
    }
}
