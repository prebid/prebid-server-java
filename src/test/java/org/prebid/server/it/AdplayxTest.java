package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.prebid.server.model.Endpoint;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

public class AdplayxTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdplayx() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/adplayx-exchange"))
                .withQueryParam("apptoken", equalTo("test_app_token"))
                .withQueryParam("placementid", equalTo("test_placement_id"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/adplayx/test-adplayx-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/adplayx/test-adplayx-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/adplayx/test-auction-adplayx-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/adplayx/test-auction-adplayx-response.json", response,
                singletonList("adplayx"));
    }
}
