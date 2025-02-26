package org.prebid.server.it;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.prebid.server.model.Endpoint;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

public class ConnatixTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBannerBidFromConnatix() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(WireMock.post(urlPathEqualTo("/connatix-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/connatix/test-banner-connatix-bid-request.json")))
                .willReturn(WireMock.aResponse().withBody(
                        jsonFrom("openrtb2/connatix/test-banner-connatix-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/connatix/test-banner-auction-connatix-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/connatix/test-banner-auction-connatix-response.json", response,
                singletonList("connatix"));
    }
}
