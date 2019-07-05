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
public class AdkernelAdnTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdkerneladn() throws IOException, JSONException {
        // given
        // adkernelAdn bid response for imp 021
        wireMockRule.stubFor(post(urlPathEqualTo("/adkernelAdn-exchange"))
                .withQueryParam("account", equalTo("101"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=UTF-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("x-openrtb-version", equalTo("2.5"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/adkerneladn/test-adkerneladn-bid-request-1.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/adkerneladn/test-adkerneladn-bid-response-1.json"))));

        // adkernelAdn bid response for imp 022
        wireMockRule.stubFor(post(urlPathEqualTo("/adkernelAdn-exchange"))
                .withQueryParam("account", equalTo("102"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=UTF-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("x-openrtb-version", equalTo("2.5"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/adkerneladn/test-adkerneladn-bid-request-2.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/adkerneladn/test-adkerneladn-bid-response-2.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/adkerneladn/test-cache-adkerneladn-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/adkerneladn/test-cache-adkerneladn-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"adkernelAdn":"AK-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImFka2VybmVsQWRuIjoiQUstVUlEIn19")
                .body(jsonFrom("openrtb2/adkerneladn/test-auction-adkerneladn-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/adkerneladn/test-auction-adkerneladn-response.json",
                response, singletonList("adkernelAdn"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
