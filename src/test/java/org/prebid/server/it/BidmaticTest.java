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

public class BidmaticTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromBidmatic() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/bidmatic-exchange"))
                .withQueryParam("source", equalTo("1000"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/bidmatic/test-bidmatic-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/bidmatic/test-bidmatic-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/bidmatic/test-auction-bidmatic-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/bidmatic/test-auction-bidmatic-response.json", response,
                singletonList("bidmatic"));
    }
}
