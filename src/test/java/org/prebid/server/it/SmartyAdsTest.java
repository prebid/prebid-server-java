package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class SmartyAdsTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromSmartyAds() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/smartyads-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/smartyads/test-smartyads-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/smartyads/test-smartyads-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/smartyads/test-auction-smartyads-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/smartyads/test-auction-smartyads-response.json", response,
                singletonList("smartyads"));
    }
}
