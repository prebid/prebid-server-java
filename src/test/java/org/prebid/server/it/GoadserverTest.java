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

public class GoadserverTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromGoadserver() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/goadserver-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/goadserver/test-goadserver-bid-request.json")))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/goadserver/test-goadserver-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/goadserver/test-auction-goadserver-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/goadserver/test-auction-goadserver-response.json", response,
                singletonList("goadserver"));
    }
}
