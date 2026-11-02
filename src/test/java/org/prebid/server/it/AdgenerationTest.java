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

public class AdgenerationTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdgeneration() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/adgeneration-exchange"))
                .withQueryParam("id", equalTo("id"))
                .withQueryParam("posall", equalTo("SSPLOC"))
                .withQueryParam("sdktype", equalTo("0"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/adgeneration/test-adgeneration-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/adgeneration/test-adgeneration-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/adgeneration/test-auction-adgeneration-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/adgeneration/test-auction-adgeneration-response.json", response,
                singletonList("adgeneration"));
    }
}
