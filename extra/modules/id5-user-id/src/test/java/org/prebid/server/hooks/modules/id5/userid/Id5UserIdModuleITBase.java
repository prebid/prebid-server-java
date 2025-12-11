package org.prebid.server.hooks.modules.id5.userid;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Uid;
import com.iab.openrtb.request.User;
import org.junit.jupiter.api.BeforeEach;
import org.prebid.server.it.IntegrationTest;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.springframework.test.context.TestPropertySource;

import java.util.Collections;
import java.util.Map;

/**
 * Base class for ID5 User ID Module integration tests.
 * <p>
 * Provides common WireMock setup and helper methods for testing the ID5 module
 * in a full Prebid Server environment.
 */
@TestPropertySource(properties = {
        // ID5 Module Configuration
        "hooks.id5-user-id.enabled=true",
        "hooks.id5-user-id.partner=173",
        "hooks.id5-user-id.inserter-name=prebid-server",
        "hooks.id5-user-id.fetch-endpoint=http://localhost:8090/id5-fetch",
        // Settings Configuration - use custom test-app-settings.yaml for ID5 hooks
        "settings.filesystem.settings-filename=src/test/resources/test-app-settings.yaml",
        "settings.filesystem.stored-requests-dir=",
        "settings.filesystem.stored-imps-dir=",
        "settings.filesystem.profiles-dir=",
        "settings.filesystem.stored-responses-dir=",
        "settings.filesystem.categories-dir="
        // Note: Generic adapter is already configured in base test-application.properties
})
public abstract class Id5UserIdModuleITBase extends IntegrationTest {

    protected static final String ID5_FETCH_PATH = "/id5-fetch/173.json";
    protected static final String GENERIC_EXCHANGE_PATH = "/generic-exchange";
    protected static final String APPNEXUS_EXCHANGE_PATH = "/appnexus-exchange";
    protected static final String TEST_ACCOUNT_ID = "test-account-id5";
    protected static final String TEST_ID5_VALUE = "ID5*test-e2e-id5-user-id";

    @BeforeEach
    public void setUpId5Mocks() {
        // Mock successful ID5 API response
        WIRE_MOCK_RULE.stubFor(WireMock.post(WireMock.urlPathEqualTo(ID5_FETCH_PATH))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "ids": {
                                    "id5": {
                                      "eid": {
                                        "source": "id5-sync.com",
                                        "uids": [{
                                          "id": "ID5*test-e2e-id5-user-id",
                                          "atype": 1
                                        }]
                                      }
                                    }
                                  }
                                }
                                """)));

        // Mock generic bidder response
        WIRE_MOCK_RULE.stubFor(WireMock.post(WireMock.urlPathEqualTo(GENERIC_EXCHANGE_PATH))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "test-request-id",
                                  "seatbid": [{
                                    "seat": "generic",
                                    "bid": [{
                                      "id": "bid-id-1",
                                      "impid": "imp-id-1",
                                      "price": 5.00,
                                      "adm": "<creative>",
                                      "crid": "creative-1",
                                      "w": 300,
                                      "h": 250
                                    }]
                                  }],
                                  "cur": "USD"
                                }
                                """)));

        // Mock appnexus bidder response
        WIRE_MOCK_RULE.stubFor(WireMock.post(WireMock.urlPathEqualTo(APPNEXUS_EXCHANGE_PATH))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "test-request-id",
                                  "seatbid": [{
                                    "seat": "appnexus",
                                    "bid": [{
                                      "id": "bid-id-2",
                                      "impid": "imp-id-1",
                                      "price": 3.50,
                                      "adm": "<creative-appnexus>",
                                      "crid": "creative-2",
                                      "w": 300,
                                      "h": 250
                                    }]
                                  }],
                                  "cur": "USD"
                                }
                                """)));
    }

    protected String createAuctionRequestWithMultipleBidders() throws JsonProcessingException {
        final BidRequest bidRequest = BidRequest.builder()
                .id("test-request-id")
                .imp(Collections.singletonList(Imp.builder()
                        .id("imp-id-1")
                        .banner(Banner.builder()
                                .format(Collections.singletonList(Format.builder().w(300).h(250).build()))
                                .build())
                        .ext(mapper.valueToTree(Map.of(
                                "prebid", Map.of(
                                        "bidder", Map.of(
                                                "generic", Map.of("exampleProperty", "test-value"),
                                                "appnexus", Map.of("placementId", 12345)
                                        )
                                )
                        )))
                        .build()))
                .site(Site.builder()
                        .page("http://example.com")
                        .publisher(Publisher.builder().id(TEST_ACCOUNT_ID).build())
                        .build())
                .regs(Regs.builder()
                        .ext(ExtRegs.of(0, null, null, null))
                        .build())
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .debug(1)
                        .build()))
                .build();

        return mapper.writeValueAsString(bidRequest);
    }

    protected String createAuctionRequestWithExistingId5(String existingId5Value) throws JsonProcessingException {
        final BidRequest bidRequest = BidRequest.builder()
                .id("test-request-id")
                .imp(Collections.singletonList(Imp.builder()
                        .id("imp-id-1")
                        .banner(Banner.builder()
                                .format(Collections.singletonList(Format.builder().w(300).h(250).build()))
                                .build())
                        .ext(mapper.valueToTree(Map.of(
                                "prebid", Map.of(
                                        "bidder", Map.of(
                                                "generic", Map.of("exampleProperty", "test-value"),
                                                "appnexus", Map.of("placementId", 12345)
                                        )
                                )
                        )))
                        .build()))
                .site(Site.builder()
                        .page("http://example.com")
                        .publisher(Publisher.builder().id(TEST_ACCOUNT_ID).build())
                        .build())
                .user(User.builder()
                        .eids(Collections.singletonList(Eid.builder()
                                .source("id5-sync.com")
                                .uids(Collections.singletonList(Uid.builder()
                                        .id(existingId5Value)
                                        .atype(1)
                                        .build()))
                                .build()))
                        .build())
                .regs(Regs.builder()
                        .ext(ExtRegs.of(0, null, null, null))
                        .build())
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .debug(1)
                        .build()))
                .build();

        return mapper.writeValueAsString(bidRequest);
    }

    protected String createExpectedEidsJson(String id5Value) throws JsonProcessingException {
        final Map<String, Object> expected = Map.of(
                "user", Map.of(
                        "ext", Map.of(
                                "eids", Collections.singletonList(Eid.builder()
                                        .source("id5-sync.com")
                                        .uids(Collections.singletonList(Uid.builder()
                                                .id(id5Value)
                                                .atype(1)
                                                .build()))
                                        .inserter("prebid-server")
                                        .build())
                        )
                )
        );

        return mapper.writeValueAsString(expected);
    }

    protected String createExpectedEidsJsonWithoutInserter(String id5Value) throws JsonProcessingException {
        final Map<String, Object> expected = Map.of(
                "user", Map.of(
                        "ext", Map.of(
                                "eids", Collections.singletonList(Eid.builder()
                                        .source("id5-sync.com")
                                        .uids(Collections.singletonList(Uid.builder()
                                                .id(id5Value)
                                                .build()))
                                        .build())
                        )
                )
        );

        return mapper.writeValueAsString(expected);
    }

    protected String createBlockedAccountRequest() throws JsonProcessingException {
        final BidRequest bidRequest = BidRequest.builder()
                .id("test-request-id")
                .imp(Collections.singletonList(Imp.builder()
                        .id("imp-id-1")
                        .banner(Banner.builder()
                                .format(Collections.singletonList(Format.builder().w(300).h(250).build()))
                                .build())
                        .ext(mapper.valueToTree(Map.of(
                                "prebid", Map.of(
                                        "bidder", Map.of(
                                                "generic", Map.of("exampleProperty", "test-value"),
                                                "appnexus", Map.of("placementId", 12345)
                                        )
                                )
                        )))
                        .build()))
                .site(Site.builder()
                        .page("http://example.com")
                        .publisher(Publisher.builder().id("blocked-account").build())
                        .build())
                .regs(Regs.builder()
                        .ext(ExtRegs.of(0, null, null, null))
                        .build())
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .debug(1)
                        .build()))
                .build();

        return mapper.writeValueAsString(bidRequest);
    }

    protected io.restassured.response.Response sendAuctionRequest(String requestBody) {
        return io.restassured.RestAssured.given(SPEC)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .post("/openrtb2/auction");
    }

    protected void verifyId5FetchCalled(int expectedTimes) {
        WIRE_MOCK_RULE.verify(expectedTimes, WireMock.postRequestedFor(WireMock.urlPathEqualTo(ID5_FETCH_PATH)));
    }

    protected void verifyBidderReceivedRequestWithId5Eid(String exchangePath, String id5Value)
            throws JsonProcessingException {
        WIRE_MOCK_RULE.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo(exchangePath))
                .withRequestBody(WireMock.equalToJson(createExpectedEidsJson(id5Value), false, true)));
    }

    protected void verifyBidderReceivedRequestWithoutId5Eid(String exchangePath) {
        WIRE_MOCK_RULE.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo(exchangePath)));
        final int callsWithId5 = WIRE_MOCK_RULE.findAll(
                WireMock.postRequestedFor(WireMock.urlPathEqualTo(exchangePath))
                        .withRequestBody(WireMock.matchingJsonPath("$.user.ext.eids[?(@.source == 'id5-sync.com')]"))
        ).size();
        org.assertj.core.api.Assertions.assertThat(callsWithId5).isEqualTo(0);
    }

    protected void verifyBidderReceivedRequestWithExistingId5(String exchangePath, String id5Value)
            throws JsonProcessingException {
        WIRE_MOCK_RULE.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo(exchangePath))
                .withRequestBody(WireMock.equalToJson(
                        createExpectedEidsJsonWithoutInserter(id5Value), false, true)));
    }
}
