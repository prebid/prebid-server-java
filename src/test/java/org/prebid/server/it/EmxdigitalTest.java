package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToIgnoreCase;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class EmxdigitalTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromEmxdigital() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/emx_digital-exchange"))
                .withQueryParam("t", matching("^[0-9]*$"))
                .withQueryParam("ts", matching("^[0-9]*$"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Forwarded-For", equalTo("193.168.244.1"))
                .withHeader("Referer", equalTo("http://www.example.com"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/emxdigital/test-emxdigital-bid-request.json"),
                        true, false))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/emxdigital/test-emxdigital-bid-response.json"))));

        // when
        final Response response =
                responseFor("openrtb2/emxdigital/test-auction-emxdigital-request.json", Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/emxdigital/test-auction-emxdigital-response.json",
                response, singletonList("emx_digital"));
    }
}

