package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class OrbidderTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromOrbidder() throws IOException, JSONException {
        // given
        // OrbidderBidder bid response for imp 001
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/orbidder-exchange"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/orbidder/test-orbidder-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/orbidder/test-orbidder-bid-response.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/orbidder/test-cache-orbidder-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/orbidder/test-cache-orbidder-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"orbidder":"OB-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7Im9yYmlkZGVyIjoiT0ItVUlEIn19")
                .body(jsonFrom("openrtb2/orbidder/test-auction-orbidder-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/orbidder/test-auction-orbidder-response.json",
                response, singletonList("orbidder"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
