package org.prebid.server.it;

import io.netty.handler.codec.http.HttpResponseStatus;
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

public class GoldbachTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromGoldbach() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/goldbach-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/goldbach/test-goldbach-bid-request.json")))
                .willReturn(aResponse()
                        .withStatus(HttpResponseStatus.CREATED.code())
                        .withBody(jsonFrom("openrtb2/goldbach/test-goldbach-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/goldbach/test-auction-goldbach-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/goldbach/test-auction-goldbach-response.json", response,
                singletonList("goldbach"));
    }
}
