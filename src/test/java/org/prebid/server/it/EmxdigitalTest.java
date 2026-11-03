package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.prebid.server.model.Endpoint;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

public class EmxdigitalTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromEmxdigital() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/emxdigital-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/emxdigital/test-emxdigital-bid-request.json"),
                        true, false))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/emxdigital/test-emxdigital-bid-response.json"))));

        // when
        final Response response =
                responseFor("openrtb2/emxdigital/test-auction-emxdigital-request.json", Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/emxdigital/test-auction-emxdigital-response.json",
                response, singletonList("emxdigital"));
    }

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromEmxUnderscoreDigital() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/emx_digital-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/emx_digital/test-emx_digital-bid-request.json"),
                        true, false))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/emx_digital/test-emx_digital-bid-response.json"))));

        // when
        final Response response =
                responseFor("openrtb2/emx_digital/test-auction-emx_digital-request.json", Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/emx_digital/test-auction-emx_digital-response.json",
                response, singletonList("emx_digital"));
    }
}
