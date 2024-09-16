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

public class Copper6SspTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromCopper6Ssp() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/copper6ssp-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/copper6ssp/test-copper6ssp-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/copper6ssp/test-copper6ssp-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/copper6ssp/test-auction-copper6ssp-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/copper6ssp/test-auction-copper6ssp-response.json", response,
                singletonList("copper6ssp"));
    }
}
