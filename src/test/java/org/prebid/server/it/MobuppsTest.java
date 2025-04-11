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

public class MobuppsTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromTheMobupps() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/mobupps-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/mobupps/test-mobupps-bid-request.json"), true, true))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/mobupps/test-mobupps-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/mobupps/test-auction-mobupps-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/mobupps/test-auction-mobupps-response.json", response,
                singletonList("mobupps"));
    }
}
