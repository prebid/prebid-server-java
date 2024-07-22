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

public class AdmanTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdman() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/adman-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/adman/test-adman-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/adman/test-adman-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/adman/test-auction-adman-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/adman/test-auction-adman-response.json", response,
                singletonList("adman"));
    }
}
