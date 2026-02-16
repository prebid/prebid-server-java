package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.prebid.server.model.Endpoint;
import org.prebid.server.version.PrebidVersionProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

public class ShowheroesbsLowercaseTest extends IntegrationTest {

    @Autowired
    private PrebidVersionProvider prebidVersionProvider;

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromShowheroesbs() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/showheroes-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/showheroes_bs/test-showheroes-bid-request.json",
                        prebidVersionProvider)))
                .willReturn(
                        aResponse().withBody(jsonFrom("openrtb2/showheroes_bs/test-showheroes-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/showheroes_bs/test-auction-showheroes-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/showheroes_bs/test-auction-showheroes-response.json", response,
                singletonList("showheroes-bs"));
    }
}
