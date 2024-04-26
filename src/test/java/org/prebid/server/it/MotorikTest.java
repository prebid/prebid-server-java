package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

public class MotorikTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromMotorik() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/motorik-exchange"))
                .withQueryParam("k", equalTo("testAccountId"))
                .withQueryParam("name", equalTo("testPlacementId"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/motorik/test-motorik-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/motorik/test-motorik-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/motorik/test-auction-motorik-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/motorik/test-auction-motorik-response.json", response,
                singletonList("motorik"));
    }
}
