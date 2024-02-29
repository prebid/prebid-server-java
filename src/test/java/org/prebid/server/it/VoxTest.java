package org.prebid.server.it;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;

@RunWith(SpringRunner.class)
public class VoxTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromVox() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(WireMock.post(WireMock.urlPathEqualTo("/vox-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/vox/test-vox-bid-request.json")))
                .willReturn(WireMock.aResponse().withBody(jsonFrom("openrtb2/vox/test-vox-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/vox/test-auction-vox-request.json", Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/vox/test-auction-vox-response.json", response, List.of("vox"));
    }
}
