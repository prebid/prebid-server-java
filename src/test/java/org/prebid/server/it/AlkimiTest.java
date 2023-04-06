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
public class AlkimiTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAlkimi() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/alkimi-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/alkimi/test-alkimi-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/alkimi/test-alkimi-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/alkimi/test-auction-alkimi-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/alkimi/test-auction-alkimi-response.json", response, singletonList("alkimi"));
    }
}
