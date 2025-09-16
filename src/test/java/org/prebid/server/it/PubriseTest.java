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

public class PubriseTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromPubrise() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/pubrise-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/pubrise/test-pubrise-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/pubrise/test-pubrise-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/pubrise/test-auction-pubrise-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/pubrise/test-auction-pubrise-response.json", response,
                singletonList("pubrise"));
    }
}
