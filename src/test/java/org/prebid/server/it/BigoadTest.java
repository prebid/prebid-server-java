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

public class BigoadTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromBigoad() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/bigoad-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/bigoad/test-bigoad-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/bigoad/test-bigoad-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/bigoad/test-auction-bigoad-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/bigoad/test-auction-bigoad-response.json", response, singletonList("bigoad"));
    }
}
