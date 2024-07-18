package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.prebid.server.model.Endpoint;

import java.io.IOException;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

public class DriftpixelTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromDriftpixel() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/driftpixel-exchange"))
                .withQueryParam("env", equalTo("env"))
                .withQueryParam("pid", equalTo("pid"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/driftpixel/test-driftpixel-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/driftpixel/test-driftpixel-bid-response.json"))));

        // when
        final Response response = responseFor(
                "openrtb2/driftpixel/test-auction-driftpixel-request.json",
                Endpoint.openrtb2_auction
        );

        // then
        assertJsonEquals("openrtb2/driftpixel/test-auction-driftpixel-response.json", response, List.of("driftpixel"));
    }

}
