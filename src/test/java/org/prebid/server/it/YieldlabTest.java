package org.prebid.server.it;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.skyscreamer.jsonassert.ArrayValueMatcher;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class YieldlabTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromYieldlab() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(WireMock.get(WireMock.urlPathEqualTo("/yieldlab-exchange/12345"))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/yieldlab/test-yieldlab-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/yieldlab/test-auction-yieldlab-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/yieldlab/test-auction-yieldlab-response.json", response,
                singletonList("yieldlab"), new Customization("seatbid[*].bid",
                        new ArrayValueMatcher<>(new CustomComparator(JSONCompareMode.NON_EXTENSIBLE,
                                new Customization("**.adm", (o1, o2) -> true),
                                new Customization("**.crid", (o1, o2) -> true)))));
    }
}
