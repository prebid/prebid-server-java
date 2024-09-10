package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.prebid.server.model.Endpoint;

import java.io.IOException;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

public class CointrafficTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromCointraffic() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cointraffic-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/cointraffic/test-cointraffic-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/cointraffic/test-cointraffic-bid-response.json"))));

        // when
        final Response response = responseFor(
                "openrtb2/cointraffic/test-auction-cointraffic-request.json",
                Endpoint.openrtb2_auction
        );

        // then
        assertJsonEquals(
                "openrtb2/cointraffic/test-auction-cointraffic-response.json",
                response,
                List.of("cointraffic"));
    }

}
