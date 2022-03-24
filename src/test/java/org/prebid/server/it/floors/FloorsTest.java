package org.prebid.server.it.floors;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.it.IntegrationTest;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class FloorsTest extends IntegrationTest {

    @Test
    public void auctionFloorsTest() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/aceex-exchange"))
                .withRequestBody(equalToJson(jsonFrom("floors/floors-test-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("floors/floors-test-bid-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .body(jsonFrom("floors/floors-test-auction-request-1.json"))
                .post("/openrtb2/auction");

        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "floors/floors-test-auction-response.json",
                response, singletonList("aceex"));

        // then
        JSONAssert.assertEquals(expectedAuctionResponse,
                response.asString(),
                new CustomComparator(JSONCompareMode.NON_EXTENSIBLE,
                        new Customization("ext.prebid.auctiontimestamp", (o1, o2) -> true)));

    }

    @Test
    public void auctionFloorsTestWithFetch() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/aceex-exchange"))
                .withRequestBody(equalToJson(jsonFrom("floors/floors-test-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("floors/floors-test-bid-response.json"))));

        // when
        final Response response1 = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .body(jsonFrom("floors/floors-test-auction-request-2.json"))
                .post("/openrtb2/auction");

        final String expectedAuctionResponse1 = openrtbAuctionResponseFrom(
                "floors/floors-test-auction-response.json",
                response1, singletonList("aceex"));

        // then
        JSONAssert.assertEquals(expectedAuctionResponse1,
                response1.asString(),
                new CustomComparator(JSONCompareMode.NON_EXTENSIBLE,
                        new Customization("ext.prebid.auctiontimestamp", (o1, o2) -> true)));
    }
}
