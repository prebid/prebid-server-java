package org.prebid.server.it;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.prebid.server.model.Endpoint;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static java.util.Collections.singletonList;

public class FlatadsTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromFlatadsBidder() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(WireMock.post(WireMock.urlPathEqualTo("/flatads-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/flatads/test-flatads-bid-request.json")))
                .willReturn(WireMock.aResponse().withBody(
                        jsonFrom("openrtb2/flatads/test-flatads-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/flatads/test-auction-flatads-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/flatads/test-auction-flatads-response.json", response,
                singletonList("flatads"));
    }
}
