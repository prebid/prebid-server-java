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
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
public class SovrnTest extends IntegrationTest {

    private static final String SOVRN = "sovrn";

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromSovrn() throws IOException, JSONException {
        // given
        // sovrn bid response for imp 13
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/sovrn-exchange"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Forwarded-For", equalTo("193.168.244.1"))
                .withHeader("DNT", equalTo("2"))
                .withHeader("Accept-Language", equalTo("en"))
                .withCookie("ljt_reader", equalTo("990011"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/sovrn/test-sovrn-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/sovrn/test-sovrn-bid-response-1.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/sovrn/test-cache-sovrn-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/sovrn/test-cache-sovrn-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"sovrn":"990011"}}
                .cookie("uids", "eyJ1aWRzIjp7InNvdnJuIjoiOTkwMDExIn19")
                .body(jsonFrom("openrtb2/sovrn/test-auction-sovrn-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/sovrn/test-auction-sovrn-response.json",
                response, singletonList(SOVRN));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void auctionShouldRespondWithBidsFromSovrn() throws IOException {
        // given
        // sovrn bid response for ad unit 11
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/sovrn-exchange"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Forwarded-For", equalTo("193.168.244.1"))
                .withHeader("DNT", equalTo("10"))
                .withHeader("Accept-Language", equalTo("en"))
                .withCookie("ljt_reader", equalTo("990011"))
                .withRequestBody(equalToJson(jsonFrom("auction/sovrn/test-sovrn-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/sovrn/test-sovrn-bid-response-1.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("auction/sovrn/test-cache-sovrn-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/sovrn/test-cache-sovrn-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                //this uids cookie value stands for {"uids":{"sovrn":"990011"}}
                .cookie("uids", "eyJ1aWRzIjp7InNvdnJuIjoiOTkwMDExIn19")
                .queryParam("debug", "1")
                .body(jsonFrom("auction/sovrn/test-auction-sovrn-request.json"))
                .post("/auction");

        // then
        assertThat(response.header("Cache-Control")).isEqualTo("no-cache, no-store, must-revalidate");
        assertThat(response.header("Pragma")).isEqualTo("no-cache");
        assertThat(response.header("Expires")).isEqualTo("0");
        assertThat(response.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://www.example.com");

        final String expectedAuctionResponse = legacyAuctionResponseFrom(
                "auction/sovrn/test-auction-sovrn-response.json",
                response, singletonList(SOVRN));
        assertThat(response.asString()).isEqualTo(expectedAuctionResponse);
    }
}
