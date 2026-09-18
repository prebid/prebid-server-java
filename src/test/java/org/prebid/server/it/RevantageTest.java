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

public class RevantageTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromRevantage() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/revantage-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/revantage/test-revantage-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/revantage/test-revantage-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/revantage/test-auction-revantage-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/revantage/test-auction-revantage-response.json", response,
                singletonList("revantage"));
    }
}
