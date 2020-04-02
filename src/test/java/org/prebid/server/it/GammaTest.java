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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class GammaTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromGamma() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/gamma-exchange/"))
                .withQueryParam("id", equalTo("id"))
                .withQueryParam("zid", equalTo("zid"))
                .withQueryParam("wid", equalTo("wid"))
                .withQueryParam("bidid", equalTo("impId1"))
                .withQueryParam("hb", equalTo("pbmobile"))
                .withQueryParam("device_ip", equalTo("123.123.123.12"))
                .withQueryParam("device_ua", equalTo("Android Chrome/60"))
                .withQueryParam("device_ifa", equalTo("ifaId"))
                .withHeader("Accept", equalTo("*/*"))
                .withHeader("Connection", equalToIgnoreCase("keep-alive"))
                .withHeader("Cache-Control", equalTo("no-cache"))
                .withHeader("Accept-Encoding", equalTo("gzip, deflate"))
                .withHeader("User-Agent", equalTo("Android Chrome/60"))
                .withHeader("x-openrtb-version", equalTo("2.5"))
                .withHeader("X-Forwarded-For", equalTo("123.123.123.12"))
                .withHeader("Accept-Language", equalTo("fr"))
                .withHeader("DNT", equalTo("2"))
                .withRequestBody(equalTo(""))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/gamma/test-gamma-bid-response.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/gamma/test-cache-gamma-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/gamma/test-cache-gamma-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"gamma":"STR-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImdhbW1hIjoiU1RSLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/gamma/test-auction-gamma-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/gamma/test-auction-gamma-response.json",
                response, singletonList("gamma"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}

