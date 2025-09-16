package org.prebid.server.it;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.prebid.server.model.Endpoint;

import java.io.IOException;

import static java.util.Collections.singletonList;

public class LiftoffTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromLiftoff() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(WireMock.post(WireMock.urlPathEqualTo("/liftoff-exchange"))
                .withRequestBody(WireMock.equalToJson(
                        jsonFrom("openrtb2/liftoff/test-liftoff-bid-request.json")))
                .willReturn(WireMock.aResponse().withBody(
                        jsonFrom("openrtb2/liftoff/test-liftoff-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/liftoff/test-auction-liftoff-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/liftoff/test-auction-liftoff-response.json", response, singletonList("liftoff"));
    }
}
