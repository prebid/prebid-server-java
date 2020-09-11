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
import static com.github.tomakehurst.wiremock.client.WireMock.equalToIgnoreCase;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class BeintooTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromBeintoo() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/beintoo-exchange"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("User-Agent", equalTo("Android Chrome/60"))
                .withHeader("X-Forwarded-For", equalTo("193.168.244.1"))
                .withHeader("Referer", equalTo("http://www.example.com"))
                .withHeader("DNT", equalTo("2"))
                .withHeader("Accept-Language", equalTo("en"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/beintoo/test-beintoo-bid-request.json"),
                        true, true))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/beintoo/test-beintoo-bid-response.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/beintoo/test-cache-beintoo-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/beintoo/test-cache-beintoo-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"beintoo":"BT-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImJlaW50b28iOiJCVC1VSUQifX0=")
                .body(jsonFrom("openrtb2/beintoo/test-auction-beintoo-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/beintoo/test-auction-beintoo-response.json",
                response, singletonList("beintoo"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
