package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

public class AdsyieldTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromTheAdsyieldBidder() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/adsyield-exchange/test.host/123456"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/adsyield/test-adsyield-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/adsyield/test-adsyield-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/adsyield/test-auction-adsyield-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/adsyield/test-auction-adsyield-response.json", response,
                singletonList("adsyield"));
    }
}
