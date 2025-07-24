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

public class AduptechTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAduptech() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/aduptech-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/aduptech/test-aduptech-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/aduptech/test-aduptech-bid-response.json"))));

        // when
        final Response response = responseFor(
                "openrtb2/aduptech/test-auction-aduptech-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/aduptech/test-auction-aduptech-response.json", response, List.of("aduptech"));
    }

}
