package org.prebid.server.orangeclickmedia;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.prebid.server.it.IntegrationTest;
import org.prebid.server.model.Endpoint;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

public class OrangeClickMediaTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromTheOrangeClickMediaBidder() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/orangeclickmedia-exchange/test.host/123456"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/orangeclickmedia/test-orangeclickmedia-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/orangeclickmedia/test-orangeclickmedia-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/orangeclickmedia/test-auction-orangeclickmedia-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/orangeclickmedia/test-auction-orangeclickmedia-response.json", response,
                singletonList("orangeclickmedia"));
    }
}
