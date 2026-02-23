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

public class ArtechnologyTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromArtechnology() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/artechnology-exchange"))
                .withQueryParam("host", equalTo("someUniquePartnerName"))
                .withQueryParam("accountId", equalTo("someSeat"))
                .withQueryParam("sourceId", equalTo("someToken"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/artechnology/test-artechnology-bid-request.json")))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/artechnology/test-artechnology-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/artechnology/test-auction-artechnology-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/artechnology/test-auction-artechnology-response.json",
                response, singletonList("artechnology"));
    }
}
