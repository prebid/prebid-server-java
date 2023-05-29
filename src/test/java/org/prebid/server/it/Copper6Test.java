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
public class Copper6Test extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromCopper6() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/copper6-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/copper6/test-copper6-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/copper6/test-copper6-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/copper6/test-auction-copper6-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/copper6/test-auction-copper6-response.json", response,
                singletonList("copper6"));
    }
}
