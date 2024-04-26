package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

public class XtrmqbTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromTheXtrmqbBidder() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/xtrmqb-exchange/test.host/123456"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/xtrmqb/test-xtrmqb-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/xtrmqb/test-xtrmqb-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/xtrmqb/test-auction-xtrmqb-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/xtrmqb/test-auction-xtrmqb-response.json", response,
                singletonList("xtrmqb"));
    }
}
