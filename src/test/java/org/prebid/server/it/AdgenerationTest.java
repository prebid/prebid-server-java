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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class AdgenerationTest extends IntegrationTest {
    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdgeneration() throws IOException, JSONException {
        // given
        // Adgeneration bid response for imp 001
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/adgeneration-exchange"))
                .withQueryParam("posall", equalTo("SSPLOC"))
                .withQueryParam("id", equalTo("58278"))
                .withQueryParam("sdktype", equalTo("0"))
                .withQueryParam("hb", equalTo("true"))
                .withQueryParam("t", equalTo("json3"))
                .withQueryParam("currency", equalTo("USD"))
                .withQueryParam("sdkname", equalTo("prebidserver"))
                .withQueryParam("size", equalTo("300×250"))
                .withQueryParam("tp", equalTo("http://www.example.com"))
                .withQueryParam("adapterver", equalTo("1.0.1"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/adgeneration/test-adgeneration-bid-response.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/adgeneration/test-cache-adgeneration-request.json")))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/adgeneration/test-cache-adgeneration-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                .cookie("uids", "eyJ1aWRzIjp7ImFkZ2VuZXJhdGlvbiI6IkFHLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/adgeneration/test-auction-adgeneration-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/adgeneration/test-auction-adgeneration-response.json",
                response, singletonList("adgeneration"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
