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
public class LunamediaTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromLunamedia() throws IOException, JSONException {
        // given
        // Lunamedia bid response for imp
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/lunamedia-exchange"))
                .withQueryParam("pubid", equalTo("19f1b372c7548ec1fe734d2c9f8dc688"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=UTF-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("x-openrtb-version", equalTo("2.5"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/lunamedia/test-lunamedia-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/lunamedia/test-lunamedia-bid-response.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/lunamedia/test-cache-lunamedia-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/lunamedia/test-cache-lunamedia-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                .cookie("uids", "eyJ1aWRzIjp7Imx1bmFtZWRpYSI6IkxNLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/lunamedia/test-auction-lunamedia-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/lunamedia/test-auction-lunamedia-response.json",
                response, singletonList("lunamedia"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
