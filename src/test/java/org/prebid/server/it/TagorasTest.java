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

public class TagorasTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromTagoras() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/tagoras-exchange/connectionId"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/tagoras/test-tagoras-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/tagoras/test-tagoras-bid-response.json"))));

        // when
        final Response response = responseFor(
                "openrtb2/tagoras/test-auction-tagoras-request.json",
                Endpoint.openrtb2_auction
        );

        // then
        assertJsonEquals("openrtb2/tagoras/test-auction-tagoras-response.json", response, List.of("tagoras"));
    }

}
