package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
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

@RunWith(SpringRunner.class)
public class ConnectAdTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromConnectAd() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/connectad-exchange"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Forwarded-For", equalTo("193.168.244.1"))
                .withHeader("DNT", equalTo("0"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/connectad/test-connectad-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/connectad/test-connectad-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/connectad/test-auction-connectad-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/connectad/test-auction-connectad-response.json", response,
                singletonList("connectad"));
    }
}
