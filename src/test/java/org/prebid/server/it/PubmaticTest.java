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
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
public class PubmaticTest extends IntegrationTest {

    private static final String PUBMATIC = "pubmatic";

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromPubmatic() throws IOException, JSONException {
        // given
        // pubmatic bid response for imp 9
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/pubmatic-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/pubmatic/test-pubmatic-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/pubmatic/test-pubmatic-bid-response-1.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/pubmatic/test-cache-pubmatic-request.json"), true,
                        false))
                .willReturn(aResponse()
                        .withTransformers("cache-response-transformer")
                        .withTransformerParameter("matcherName",
                                "openrtb2/pubmatic/test-cache-matcher-pubmatic.json")));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"pubmatic":"PM-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InB1Ym1hdGljIjoiUE0tVUlEIn19")
                .body(jsonFrom("openrtb2/pubmatic/test-auction-pubmatic-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/pubmatic/test-auction-pubmatic-response.json",
                response, singletonList(PUBMATIC));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void auctionShouldRespondWithBidsFromPubmatic() throws IOException {
        // given
        // pubmatic bid response for ad unit 9
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/pubmatic-exchange"))
                .withRequestBody(equalToJson(jsonFrom("auction/pubmatic/test-pubmatic-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/pubmatic/test-pubmatic-bid-response-1.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("auction/pubmatic/test-cache-pubmatic-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/pubmatic/test-cache-pubmatic-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"pubmatic":"PM-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InB1Ym1hdGljIjoiUE0tVUlEIn19")
                .queryParam("debug", "1")
                .body(jsonFrom("auction/pubmatic/test-auction-pubmatic-request.json"))
                .post("/auction");

        // then
        assertThat(response.header("Cache-Control")).isEqualTo("no-cache, no-store, must-revalidate");
        assertThat(response.header("Pragma")).isEqualTo("no-cache");
        assertThat(response.header("Expires")).isEqualTo("0");
        assertThat(response.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://www.example.com");

        final String expectedAuctionResponse = legacyAuctionResponseFrom(
                "auction/pubmatic/test-auction-pubmatic-response.json",
                response, singletonList(PUBMATIC));
        assertThat(response.asString()).isEqualTo(expectedAuctionResponse);
    }
}
