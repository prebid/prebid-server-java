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

public class StreamvisionTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromTheStreamvisionBidder() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/streamvision-exchange/test.host/123456"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/streamvision/test-streamvision-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/streamvision/test-streamvision-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/streamvision/test-auction-streamvision-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/streamvision/test-auction-streamvision-response.json", response,
                singletonList("streamvision"));
    }
}
