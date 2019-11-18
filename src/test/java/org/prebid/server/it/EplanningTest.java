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
public class EplanningTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromEplanning() throws IOException, JSONException {
        // given
        // eplanning bid response for imp15
        wireMockRule.stubFor(get(urlPathEqualTo("/eplanning-exchange/12345/1/example.com/ROS"))
                .withQueryParam("r", equalTo("pbs"))
                .withQueryParam("ncb", equalTo("1"))
                .withQueryParam("ur", equalTo("http://www.example.com"))
                .withQueryParam("e", equalTo("testadunitcode:600x300"))
                .withQueryParam("ip", equalTo("193.168.244.1"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Forwarded-For", equalTo("193.168.244.1"))
                .withHeader("DNT", equalTo("2"))
                .withHeader("Accept-Language", equalTo("en"))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/eplanning/test-eplanning-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/eplanning/test-cache-eplanning-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/eplanning/test-cache-eplanning-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{""eplanning":"EP-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImVwbGFubmluZyI6IkVQLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/eplanning/test-auction-eplanning-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/eplanning/test-auction-eplanning-response.json",
                response, singletonList("eplanning"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
