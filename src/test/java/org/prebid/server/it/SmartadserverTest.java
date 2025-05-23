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
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public class SmartadserverTest extends IntegrationTest {

    private static final String SMARTADSERVER = "smartadserver";
    private static final String EQUATIV_ALIAS = "equativ";

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromSmartadserver() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/smartadserver-exchange/api/bid"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/smartadserver/test-smartadserver-bid-request.json")))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/smartadserver/test-smartadserver-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/smartadserver/test-auction-smartadserver-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/smartadserver/test-auction-smartadserver-response.json", response,
                singletonList(SMARTADSERVER));
    }

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromEquativ() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/smartadserver-exchange/api/bid"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/smartadserver/alias/test-smartadserver-bid-request.json")))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/smartadserver/alias/test-smartadserver-bid-response.json"))));

        // when
        final Response response = responseFor(
                "openrtb2/smartadserver/alias/test-auction-smartadserver-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/smartadserver/alias/test-auction-smartadserver-response.json", response,
                asList(SMARTADSERVER, EQUATIV_ALIAS));
    }
}
