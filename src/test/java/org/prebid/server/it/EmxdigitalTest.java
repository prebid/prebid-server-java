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
public class EmxdigitalTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromEmxdigital() throws IOException, JSONException {
        // given
        wireMockRule.stubFor(post(urlPathEqualTo("/emx_digital-exchange"))
                .withQueryParam("t", equalTo("1000"))
                .withQueryParam("ts", equalTo("2060541160"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("User-Agent", equalTo("Android Chrome/60"))
                .withHeader("X-Forwarded-For", equalTo("127.0.0.1"))
                .withHeader("Referer", equalTo("http://www.example.com"))
                .withHeader("DNT", equalTo("2"))
                .withHeader("Accept-Language", equalTo("en"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/emxdigital/test-emxdigital-bid-request.json"),
                        true, true))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/emxdigital/test-emxdigital-bid-response.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/emxdigital/test-cache-emxdigital-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/emxdigital/test-cache-emxdigital-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"emxdigital":"STR-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImVteGRpZ2l0YWwiOiJTVFItVUlEIn19")
                .body(jsonFrom("openrtb2/emxdigital/test-auction-emxdigital-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/emxdigital/test-auction-emxdigital-response.json",
                response, singletonList("emx_digital"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}

