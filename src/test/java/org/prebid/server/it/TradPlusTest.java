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

public class TradPlusTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromTradPlus() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/tradplus-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/tradplus/test-tradplus-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/tradplus/test-tradplus-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/tradplus/test-auction-tradplus-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/tradplus/test-auction-tradplus-response.json", response, singletonList("tradplus"));
    }
}
