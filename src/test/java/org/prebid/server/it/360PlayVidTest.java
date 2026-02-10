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

public class 360PlayVidTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFrom360PlayVid() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/360playvid-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/360playvid/test-360playvid-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/360playvid/test-360playvid-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/360playvid/test-auction-360playvid-request.json",
                Endpoint.openrtb2_auction);
    }
}
