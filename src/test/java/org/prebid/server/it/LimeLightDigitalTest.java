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

public class LimeLightDigitalTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromTheLimeLightDigitalBidder() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/limelightDigital-exchange/test.host/123456"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/limelightDigital/test-limelightDigital-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/limelightDigital/test-limelightDigital-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/limelightDigital/test-auction-limelightDigital-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/limelightDigital/test-auction-limelightDigital-response.json", response,
                singletonList("limelightDigital"));
    }
}
