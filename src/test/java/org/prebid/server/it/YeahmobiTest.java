package org.prebid.server.it;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.prebid.server.model.Endpoint;

import java.io.IOException;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;

public class YeahmobiTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromYeahmobi() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(WireMock.post(WireMock.urlPathEqualTo("/yeahmobi-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/yeahmobi/test-yeahmobi-bid-request.json")))
                .willReturn(WireMock.aResponse().withBody(
                        jsonFrom("openrtb2/yeahmobi/test-yeahmobi-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/yeahmobi/test-auction-yeahmobi-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/yeahmobi/test-auction-yeahmobi-response.json", response, List.of("yeahmobi"));
    }
}
