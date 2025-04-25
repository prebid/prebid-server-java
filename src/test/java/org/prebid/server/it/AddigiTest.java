package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.prebid.server.model.Endpoint;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

public class AddigiTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAddigi() throws IOException, JSONException {
        //given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/addigi-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/addigi/test-addigi-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/addigi/test-addigi-bid-response.json"))));

        //when
        final Response response = responseFor("openrtb2/addigi/test-auction-addigi-request.json",
                Endpoint.openrtb2_auction);

        //then
        assertJsonEquals("openrtb2/addigi/test-auction-addigi-response.json", response, singletonList("addigi"));
    }
}
