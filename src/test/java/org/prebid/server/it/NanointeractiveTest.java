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
public class NanointeractiveTest extends IntegrationTest {

    private static final String NANOINTERACTIVE = "nanointeractive";

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromNanointeractive() throws IOException, JSONException {
        // given
        // nanointeractive bid response for imp
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/nanointeractive-exchange/"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("x-openrtb-version", equalTo("2.5"))
                .withHeader("X-Forwarded-For", equalTo("193.168.244.1"))
                .withHeader("Cookie", equalTo("Nano=NI-UID"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/nanointeractive/test-nanointeractive-bid-request-1.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/nanointeractive/test-nanointeractive-bid-response-1.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/nanointeractive/test-cache-nanointeractive-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/nanointeractive/test-cache-nanointeractive-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Cookie", "Nano=NI-UID")
                .header("Origin", "http://www.example.com")
                .cookie("uids", "eyJ1aWRzIjp7Im5hbm9pbnRlcmFjdGl2ZSI6Ik5JLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/nanointeractive/test-auction-nanointeractive-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/nanointeractive/test-auction-nanointeractive-response.json",
                response, singletonList(NANOINTERACTIVE));

        String actualStr = response.asString();
        JSONAssert.assertEquals(expectedAuctionResponse, actualStr, JSONCompareMode.NON_EXTENSIBLE);
    }
}
