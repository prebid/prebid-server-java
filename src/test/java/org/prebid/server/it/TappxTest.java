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
public class TappxTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromTappx() throws IOException, JSONException {
        // given
        // tappx bid response for imp 12
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/tappx-exchange"))
                .withQueryParam("tappxkey", equalTo("pub-12345-android-9876"))
                .withQueryParam("v", equalTo("1.2"))
                .withQueryParam("type_cnn", equalTo("prebid"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/tappx/test-tappx-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/tappx/test-tappx-bid-response.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/tappx/test-cache-tappx-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/tappx/test-cache-tappx-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"tappx":"TX-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InRhcHB4IjoiVFgtVUlEIn19")
                .body(jsonFrom("openrtb2/tappx/test-auction-tappx-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/tappx/test-auction-tappx-response.json",
                response, singletonList("tappx"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}

