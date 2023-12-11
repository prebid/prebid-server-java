package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

@RunWith(SpringRunner.class)
public class Edge226Test extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromEdge226() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/edge226-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/edge226/test-edge226-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/edge226/test-edge226-bid-response.json"))));

        // when
        final Response response = responseFor(
                "openrtb2/edge226/test-auction-edge226-request.json",
                Endpoint.openrtb2_auction
        );

        // then
        assertJsonEquals("openrtb2/edge226/test-auction-edge226-response.json", response, List.of("edge226"));
    }

}
