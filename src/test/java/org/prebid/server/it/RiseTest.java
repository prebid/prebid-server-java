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

public class RiseTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromRise() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/rise-exchange"))
                .withQueryParam("publisher_id", equalTo("testOrgId"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/rise/test-rise-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/rise/test-rise-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/rise/test-auction-rise-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/rise/test-auction-rise-response.json", response,
                singletonList("rise"));
    }
}
