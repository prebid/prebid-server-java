package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class RichaudienceTest extends IntegrationTest {

    private static final String BIDDER_NAME = "richaudience";

    private static final String URI = "/%s-exchange";
    private static final String BID_REQUEST = "openrtb2/%s/test-%s-bid-request.json";
    private static final String BID_RESPONSE = "openrtb2/%s/test-%s-bid-response.json";
    private static final String AUCTION_REQUEST = "openrtb2/%s/test-auction-%s-request.json";
    private static final String AUCTION_RESPONSE = "openrtb2/%s/test-auction-%s-response.json";

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromRichaudience() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo(getUri()))
                .withRequestBody(equalToJson(jsonFrom(getFile(BID_REQUEST))))
                .willReturn(aResponse().withBody(jsonFrom(getFile(BID_RESPONSE)))));

        // when
        final Response response = responseFor(getFile(AUCTION_REQUEST), Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(getFile(AUCTION_RESPONSE), response, singletonList(BIDDER_NAME));
    }

    private static String getUri() {
        return String.format(URI, BIDDER_NAME);
    }

    private static String getFile(String filePattern) {
        return String.format(filePattern, BIDDER_NAME, BIDDER_NAME);
    }
}
