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

public class AdQueryTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdquery() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/adquery-exchange"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/adquery/test-adquery-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/adquery/test-adquery-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/adquery/test-auction-adquery-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/adquery/test-auction-adquery-response.json", response,
                singletonList("adquery"));
    }
}
