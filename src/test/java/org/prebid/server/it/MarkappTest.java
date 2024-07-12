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

public class MarkappTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromMarkapp() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/markapp-exchange"))
                .withQueryParam("host", equalTo("someUniquePartnerName"))
                .withQueryParam("accountId", equalTo("someSeat"))
                .withQueryParam("sourceId", equalTo("someToken"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/markapp/test-markapp-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/markapp/test-markapp-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/markapp/test-auction-markapp-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/markapp/test-auction-markapp-response.json", response, singletonList("markapp"));
    }
}
