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
public class AdkernelAdnTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdkerneladn() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/adkernelAdn.tag.adkernel.com"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/adkerneladn/test-adkerneladn-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/adkerneladn/test-adkerneladn-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/adkerneladn/test-auction-adkerneladn-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/adkerneladn/test-auction-adkerneladn-response.json", response,
                singletonList("adkernelAdn"));
    }
}
