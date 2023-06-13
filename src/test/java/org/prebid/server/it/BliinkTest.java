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
public class BliinkTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromTheBliinkBidder() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/bliink-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/bliink/test-bliink-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/bliink/test-bliink-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/bliink/test-auction-bliink-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/bliink/test-auction-bliink-response.json", response, singletonList("bliink"));
    }
}
