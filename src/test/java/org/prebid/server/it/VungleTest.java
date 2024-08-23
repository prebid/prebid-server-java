package org.prebid.server.it;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.prebid.server.model.Endpoint;

import java.io.IOException;

import static java.util.Collections.singletonList;

public class VungleTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromVungle() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(WireMock.post(WireMock.urlPathEqualTo("/vungle-exchange"))
                .withRequestBody(WireMock.equalToJson(
                        jsonFrom("openrtb2/vungle/test-vungle-bid-request.json")))
                .willReturn(WireMock.aResponse().withBody(
                        jsonFrom("openrtb2/vungle/test-vungle-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/vungle/test-auction-vungle-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/vungle/test-auction-vungle-response.json", response, singletonList("vungle"));
    }
}
