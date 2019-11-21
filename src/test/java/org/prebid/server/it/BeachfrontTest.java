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
public class BeachfrontTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromBeachfront() throws IOException, JSONException {
        // given
        // beachfront bid response for imp 02
        wireMockRule.stubFor(post(urlPathEqualTo("/beachfront-exchange/video"))
                .withQueryParam("exchange_id", equalTo("beachfrontAppId"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=UTF-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("Host", equalTo("localhost:8090"))
                .withHeader("Accept-Language", equalTo("en"))
                .withHeader("DNT", equalTo("2"))
                .withHeader("Cookie", equalTo("__io_cid=BF-UID"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/beachfront/test-beachfront-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/beachfront/test-beachfront-bid-response-2.json"))));

        // beachfront bid response for imp 01
        wireMockRule.stubFor(post(urlPathEqualTo("/beachfront-exchange/video"))
                .withQueryParam("exchange_id", equalTo("beachfrontAppId1"))
                .withQueryParam("prebidserver", equalTo(""))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=UTF-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("Host", equalTo("localhost:8090"))
                .withHeader("Accept-Language", equalTo("en"))
                .withHeader("DNT", equalTo("2"))
                .withHeader("Cookie", equalTo("__io_cid=BF-UID"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/beachfront/test-beachfront-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/beachfront/test-beachfront-bid-response-1.json"))));

        // beachfront bid response for imp 03
        wireMockRule.stubFor(post(urlPathEqualTo("/beachfront-exchange/banner"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=UTF-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("Host", equalTo("localhost:8090"))
                .withHeader("Accept-Language", equalTo("en"))
                .withHeader("DNT", equalTo("2"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/beachfront/test-beachfront-bid-request-3.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/beachfront/test-beachfront-bid-response-3.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/beachfront/test-cache-beachfront-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/beachfront/test-cache-beachfront-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                .body(jsonFrom("openrtb2/beachfront/test-auction-beachfront-request.json"))
                // this uids cookie value stands for {"uids":{"beachfront":"BF-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImJlYWNoZnJvbnQiOiJCRi1VSUQifX0=")
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/beachfront/test-auction-beachfront-response.json",
                response, singletonList("beachfront"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
