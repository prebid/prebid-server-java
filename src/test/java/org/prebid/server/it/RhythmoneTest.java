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
public class RhythmoneTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromRhythmone() throws IOException, JSONException {
        // given
        // rhythmone bid response for imp002
        wireMockRule.stubFor(post(urlPathEqualTo("/rhythmone-exchange/72721/0/mvo"))
                .withQueryParam("z", equalTo("1r"))
                .withQueryParam("s2s", equalTo("true"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/rhythmone/test-rhythmone-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/rhythmone/test-rhythmone-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/rhythmone/test-cache-rhythmone-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/rhythmone/test-cache-rhythmone-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"rhythmone":"RO-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InJoeXRobW9uZSI6IlJPLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/rhythmone/test-auction-rhythmone-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/rhythmone/test-auction-rhythmone-response.json",
                response, singletonList("rhythmone"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
