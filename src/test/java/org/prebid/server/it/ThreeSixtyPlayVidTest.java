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

public class ThreeSixtyPlayVidTest extends IntegrationTest {

    private static final String BID_REQUEST_JSON = "openrtb2/360playvid/test-360playvid-bid-request.json";
    private static final String BID_RESPONSE_JSON = "openrtb2/360playvid/test-360playvid-bid-response.json";
    private static final String AUCTION_REQUEST_JSON = "openrtb2/360playvid/test-auction-360playvid-request.json";
    private static final String AUCTION_RESPONSE_JSON = "openrtb2/360playvid/test-auction-360playvid-response.json";

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromThreeSixtyPlayVid() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/360playvid-exchange"))
                .withRequestBody(equalToJson(jsonFrom(BID_REQUEST_JSON)))
                .willReturn(aResponse().withBody(jsonFrom(BID_RESPONSE_JSON))));

        // when
        final Response response = responseFor(AUCTION_REQUEST_JSON,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(AUCTION_RESPONSE_JSON, response, singletonList("360playvid"));
    }
}
