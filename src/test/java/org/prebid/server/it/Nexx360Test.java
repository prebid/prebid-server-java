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

public class Nexx360Test extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromNexx360() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/nexx360-exchange"))
                .withQueryParam("placement", equalTo("placement"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/nexx360/test-nexx360-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/nexx360/test-nexx360-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/nexx360/test-auction-nexx360-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/nexx360/test-auction-nexx360-response.json", response, singletonList("nexx360"));
    }
}
