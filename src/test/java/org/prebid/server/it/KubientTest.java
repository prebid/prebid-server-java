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

@RunWith(SpringRunner.class)
public class KubientTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromKubient() throws IOException, JSONException {
        // given
        // Kubient bid response for imp 001 and 002
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/kubient-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/kubient/test-kubient-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/kubient/test-kubient-bid-response-1.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/kubient/test-cache-kubient-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/kubient/test-cache-kubient-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"kub":"KUM-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7Imt1YiI6IktVTS1VSUQifX0=")
                .body(jsonFrom("openrtb2/kubient/test-auction-kubient-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/kubient/test-auction-kubient-response.json",
                response, singletonList("kubient"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}

