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

public class PubnativeTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromThePubnative() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/pubnative-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/pubnative/test-pubnative-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/pubnative/test-pubnative-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/pubnative/test-auction-pubnative-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/pubnative/test-auction-pubnative-response.json", response,
                singletonList("pubnative"));
    }
}
