package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.prebid.server.model.Endpoint;
import org.prebid.server.version.PrebidVersionProvider;
import org.skyscreamer.jsonassert.Customization;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

public class MagniteTest extends IntegrationTest {

    @Autowired
    private PrebidVersionProvider versionProvider;

    @Test
    public void testOpenrtb2AuctionCoreFunctionality() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/magnite-exchange"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/magnite/test-magnite-bid-request.json", versionProvider)))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/magnite/test-magnite-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/magnite/test-auction-magnite-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/magnite/test-auction-magnite-response.json",
                response,
                singletonList("magnite"),
                new Customization("seatbid[*].bid[*].id", (o1, o2) -> true));
    }
}
