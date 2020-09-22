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
public class TelariaTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromTelaria() throws IOException, JSONException {
        // given
        // ValueImpression bid response
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/telaria-exchange/"))

                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Forwarded-For", equalTo("193.168.244.1"))
                .withHeader("x-openrtb-version", equalTo("2.5"))
                .withHeader("Accept-Encoding", equalTo("gzip"))
                .withHeader("Accept-Language", equalTo("en"))
                .withHeader("Content-Length", equalTo("677"))
                .withHeader("DNT", equalTo("2"))
                .withHeader("Host", equalTo("localhost:8090"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/telaria/test-telaria-bid-request-1.json")))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/telaria/test-telaria-bid-response-1.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/telaria/test-cache-telaria-request.json")))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/telaria/test-cache-telaria-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                .cookie("uids", "eyJ1aWRzIjp7InRlbGFyaWEiOiJUTC1VSUQifX0=")
                .body(jsonFrom("openrtb2/telaria/test-auction-telaria-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/telaria/test-auction-telaria-response.json",
                response, singletonList("telaria"));

        String actualStr = response.asString();
        JSONAssert.assertEquals(expectedAuctionResponse, actualStr, JSONCompareMode.NON_EXTENSIBLE);
    }
}
