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
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class MarsmediaTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromMarsmedia() throws IOException, JSONException {
        // given
        // Marsmedia bid response for imp 001
        wireMockRule.stubFor(post(urlPathEqualTo("/marsmedia-exchange&zone=zone_1"))
                //.withQueryParam("zone", equalTo("zone_1"))
                .withHeader("x-openrtb-version", equalTo("2.5"))
                .withHeader("DNT", equalTo("2"))
                .withHeader("Accept-Language", equalTo("en"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/marsmedia/test-marsmedia-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/marsmedia/test-marsmedia-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/marsmedia/test-cache-marsmedia-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/marsmedia/test-cache-marsmedia-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"marsmedia":"MM-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7Im1hcnNtZWRpYSI6Ik1NLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/marsmedia/test-auction-marsmedia-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/marsmedia/test-auction-marsmedia-response.json",
                response, singletonList("marsmedia"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
