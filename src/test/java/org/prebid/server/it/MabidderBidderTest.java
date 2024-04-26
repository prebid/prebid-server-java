package org.prebid.server.it;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;

public class MabidderBidderTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromMaBidder() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(WireMock.post(WireMock.urlPathEqualTo("/mabidder-exchange"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/mabidder/test-mabidder-bid-request.json")))
                .willReturn(WireMock.aResponse().withBody(
                        jsonFrom("openrtb2/mabidder/test-mabidder-bid-response.json"))));

        // when
        final Response response = responseFor(
                "openrtb2/mabidder/test-auction-mabidder-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/mabidder/test-auction-mabidder-response.json", response, List.of("mabidder"));
    }
}
