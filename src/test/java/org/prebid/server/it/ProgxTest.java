package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.prebid.server.model.Endpoint;

import java.io.IOException;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

public class ProgxTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromProgx() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/progx-exchange/"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/progx/test-progx-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/progx/test-progx-bid-response.json"))));

        // when
        final Response response = responseFor(
                "openrtb2/progx/test-auction-progx-request.json",
                Endpoint.openrtb2_auction
        );

        // then
        assertJsonEquals("openrtb2/progx/test-auction-progx-response.json", response, List.of("progx"));
    }

}
