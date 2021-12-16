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
public class GamoshiTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromGamoshi() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/gamoshi-exchange/r/1701/bidr"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/gamoshi/test-gamoshi-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/gamoshi/test-gamoshi-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/gamoshi/test-auction-gamoshi-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/gamoshi/test-auction-gamoshi-response.json", response,
                singletonList("gamoshi"));
    }
}
