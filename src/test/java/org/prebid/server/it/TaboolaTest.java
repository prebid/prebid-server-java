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

public class TaboolaTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromTappx() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/taboola-exchange"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/taboola/test-taboola-bid-request-banner.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/taboola/test-taboola-bid-response-banner.json"))));

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/taboola-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/taboola/test-taboola-bid-request-native.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/taboola/test-taboola-bid-response-native.json"))));
        // when
        final Response response = responseFor("openrtb2/taboola/test-auction-taboola-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/taboola/test-auction-taboola-response.json", response, singletonList("taboola"));
    }
}
