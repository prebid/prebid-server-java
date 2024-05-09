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
public class PulsepointTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromPulsepoint() throws IOException, JSONException {
        // pulsepoint params as integers
        openrtb2AuctionShouldRespondWithBidsFromPulsepoint(
                "openrtb2/pulsepoint/test-auction-pulsepoint-request.json",
                "openrtb2/pulsepoint/test-pulsepoint-bid-request.json");

        // pulsepoint params as string
        openrtb2AuctionShouldRespondWithBidsFromPulsepoint(
                "openrtb2/pulsepoint/test-auction-pulsepoint-request-params-as-string.json",
                "openrtb2/pulsepoint/test-pulsepoint-bid-request-params-as-string.json");
    }

    private void openrtb2AuctionShouldRespondWithBidsFromPulsepoint(String auctionFilePath,
                                                                    String pulsepointRequestPath)
            throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/pulsepoint-exchange"))
                .withRequestBody(equalToJson(jsonFrom(pulsepointRequestPath)))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/pulsepoint/test-pulsepoint-bid-response.json"))));

        // when
        final Response response = responseFor(auctionFilePath,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/pulsepoint/test-auction-pulsepoint-response.json", response,
                singletonList("pulsepoint"));
    }
}
