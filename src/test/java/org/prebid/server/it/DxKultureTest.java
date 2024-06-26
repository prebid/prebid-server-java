package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.prebid.server.model.Endpoint;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

public class DxKultureTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromTheDxKultureBidder() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/dxkulture-exchange"))
                .withQueryParam("publisher_id", equalTo("testPublisherId"))
                .withQueryParam("placement_id", equalTo("testPlacementId"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/dxkulture/test-dxkulture-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/dxkulture/test-dxkulture-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/dxkulture/test-auction-dxkulture-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/dxkulture/test-auction-dxkulture-response.json", response,
                singletonList("dxkulture"));
    }
}
