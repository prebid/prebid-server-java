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
public class BetweenTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromBetween() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/between-exchange"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Forwarded-For", equalTo("193.168.244.1"))
                .withHeader("Referer", equalTo("http://www.example.com"))
                .withQueryParam("host", equalTo("lbs-ru1.ads"))
                .withQueryParam("pubId", equalTo("publisherTestID"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/between/test-between-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/between/test-between-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/between/test-auction-between-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/between/test-auction-between-response.json", response, singletonList("between"));
    }
}
