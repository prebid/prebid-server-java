package org.prebid.server.it;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static java.util.Collections.singletonList;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.response.Response;

import java.io.IOException;

import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.prebid.server.model.Endpoint;

public class FeedAdTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromFeedAdBidder() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(WireMock.post(WireMock.urlPathEqualTo("/feedad-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/feedad/test-feedad-bid-request.json")))
                .willReturn(WireMock.aResponse().withBody(jsonFrom("openrtb2/feedad/test-feedad-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/feedad/test-auction-feedad-request.json",
                Endpoint.openrtb2_auction);

        System.out.println(response.asString());

        // then
        assertJsonEquals("openrtb2/feedad/test-auction-feedad-response.json", response,
                singletonList("feedad"));
    }
}
