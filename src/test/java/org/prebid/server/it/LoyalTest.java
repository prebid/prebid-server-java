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
public class LoyalTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromLoyal() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/loyal-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/loyal/test-loyal-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/loyal/test-loyal-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/loyal/test-auction-loyal-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/loyal/test-auction-loyal-response.json", response, singletonList("loyal"));
    }
}
