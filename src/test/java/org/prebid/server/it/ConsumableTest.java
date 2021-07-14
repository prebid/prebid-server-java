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
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class ConsumableTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromConsumable() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/consumable-exchange"))
                .withHeader("Origin", equalTo("http://www.example.com"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("Forwarded", equalTo("for=193.168.244.1"))
                .withHeader("Referer", equalTo("http://www.example.com"))
                .withHeader("X-Forwarded-For", equalTo("193.168.244.1"))
                .withHeader("Host", equalTo("localhost:8090"))
                .withHeader("Content-Length", matching("[0-9]*"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/consumable/test-consumable-bid-request-1.json"),
                        true, true))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/consumable/test-consumable-bid-response-1.json"))));

        // when
        final Response response = responseFor("openrtb2/consumable/test-auction-consumable-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/consumable/test-auction-consumable-response.json", response,
                singletonList("consumable"));
    }
}
