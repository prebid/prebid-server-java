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
public class KidozTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromKidoz() throws IOException, JSONException {
        // given
        // Kidoz bid response for imp 001
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/kidoz-exchange"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withHeader("x-openrtb-version", equalTo("2.5"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/kidoz/test-kidoz-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/kidoz/test-kidoz-bid-response-1.json"))));

        // Kidoz bid response for imp 002
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/kidoz-exchange"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withHeader("x-openrtb-version", equalTo("2.5"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/kidoz/test-kidoz-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/kidoz/test-kidoz-bid-response-2.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/kidoz/test-cache-kidoz-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/kidoz/test-cache-kidoz-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                .cookie("uids", "eyJ1aWRzIjp7ImtpZG96IjoiS1otVUlEIn19")
                .body(jsonFrom("openrtb2/kidoz/test-auction-kidoz-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/kidoz/test-auction-kidoz-response.json",
                response, singletonList("kidoz"));

        String actualStr = response.asString();
        JSONAssert.assertEquals(expectedAuctionResponse, actualStr, JSONCompareMode.NON_EXTENSIBLE);
    }
}
