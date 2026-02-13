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

public class ElementalTVTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromElementalTV() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/elementaltv-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/elementaltv/test-elementaltv-bid-request-1.json")))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/elementaltv/test-elementaltv-bid-response-1.json"))));

        // when
        final Response response = responseFor("openrtb2/elementaltv/test-auction-elementaltv-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/elementaltv/test-auction-elementaltv-response.json", response,
                singletonList("elementaltv"));
    }
}
