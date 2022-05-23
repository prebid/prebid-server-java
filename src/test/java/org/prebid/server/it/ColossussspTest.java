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
public class ColossussspTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromColossusssp() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/colossusssp-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/colossus/aliases/test-colossusssp-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/colossus/aliases/test-colossusssp-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/colossus/aliases/test-auction-colossusssp-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/colossus/aliases/test-auction-colossusssp-response.json", response,
                singletonList("colossusssp"));
    }
}
