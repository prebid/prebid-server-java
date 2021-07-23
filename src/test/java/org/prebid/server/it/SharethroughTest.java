package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.skyscreamer.jsonassert.Customization;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class SharethroughTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromSharethrough() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/sharethrough-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/sharethrough/test-sharethrough-request.json")))
                .willReturn(
                        aResponse().withBody(jsonFrom("openrtb2/sharethrough/test-sharethrough-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/sharethrough/test-auction-sharethrough-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/sharethrough/test-auction-sharethrough-response.json", response,
                singletonList("sharethrough"),
                new Customization("seatbid[group=0].bid[adid=arid].adm", (o1, o2) -> true));
    }
}
