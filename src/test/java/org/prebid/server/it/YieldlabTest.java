package org.prebid.server.it;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class YieldlabTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromYieldlab() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(WireMock.get(WireMock.urlPathEqualTo("/yieldlab-exchange/12345"))
                .withQueryParam("content", WireMock.equalTo("json"))
                .withQueryParam("pvid", WireMock.equalTo("true"))
                //harcoded value of ts to pass test
                .withQueryParam("ts", WireMock.equalTo("200000"))
                .withQueryParam("t", WireMock.equalTo("key1=value1&key2=value2"))
                .withQueryParam("ids", WireMock.equalTo("YL-UID"))
                .withQueryParam("yl_rtb_ifa", WireMock.equalTo("ifaId"))
                .withQueryParam("yl_rtb_devicetype", WireMock.equalTo("4"))
                .withQueryParam("yl_rtb_connectiontype", WireMock.equalTo("6"))
                .withQueryParam("lat", WireMock.equalTo("51.49949"))
                .withQueryParam("lon", WireMock.equalTo("-0.128953"))
                .withQueryParam("gdpr", WireMock.equalTo("0"))
                .withQueryParam("consent", WireMock.equalTo("consentValue"))
                .withHeader("Accept", WireMock.equalTo("application/json"))
                .withHeader("User-Agent", WireMock.equalTo("userAgent"))
                .withHeader("X-Forwarded-For", WireMock.equalTo("193.168.244.1"))
                .withHeader("Cookie", WireMock.equalTo("id=YL-UID"))
                .willReturn(WireMock.aResponse()
                        .withBody(jsonFrom("openrtb2/yieldlab/test-yieldlab-bid-response.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(WireMock.post(WireMock.urlPathEqualTo("/cache"))
                .withRequestBody(WireMock
                        .equalToJson(jsonFrom("openrtb2/yieldlab/test-cache-yieldlab-request.json")))
                .willReturn(WireMock.aResponse()
                        .withBody(jsonFrom("openrtb2/yieldlab/test-cache-yieldlab-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"yieldlab":"YL-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InlpZWxkbGFiIjoiWUwtVUlEIn19")
                .body(jsonFrom("openrtb2/yieldlab/test-auction-yieldlab-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/yieldlab/test-auction-yieldlab-response.json",
                response, singletonList("yieldlab"));

        final String actualStr = response.asString();
        JSONAssert.assertEquals(expectedAuctionResponse, actualStr, openrtbCacheDebugComparator());
    }
}
