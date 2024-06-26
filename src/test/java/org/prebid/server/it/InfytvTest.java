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

public class InfytvTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromInfytv() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/infytv-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/infytv/test-infytv-bid-request-1.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/infytv/test-infytv-bid-response-1.json"))));

        // when
        final Response response = responseFor("openrtb2/infytv/test-auction-infytv-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/infytv/test-auction-infytv-response.json", response,
                singletonList("infytv"));
    }
}
