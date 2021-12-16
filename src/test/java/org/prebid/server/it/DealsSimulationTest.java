package org.prebid.server.it;

import com.github.tomakehurst.wiremock.client.VerificationException;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import com.github.tomakehurst.wiremock.matching.AnythingPattern;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.json.JSONException;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.VertxTest;
import org.prebid.server.deals.LineItemService;
import org.skyscreamer.jsonassert.ArrayValueMatcher;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.ValueMatcher;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@RunWith(SpringRunner.class)
@TestPropertySource(locations = {
        "test-application.properties",
        "deals/test-deals-application.properties",
        "deals/test-deals-simulation-application.properties"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class DealsSimulationTest extends VertxTest {

    private static final int APP_PORT = 10080;
    private static final int WIREMOCK_PORT = 8090;

    private static final RequestSpecification SPEC = IntegrationTest.spec(APP_PORT);

    @ClassRule
    public static final WireMockClassRule WIRE_MOCK_RULE = new WireMockClassRule(options().port(WIREMOCK_PORT));

    private static final ZonedDateTime NOW = ZonedDateTime.now(
            Clock.fixed(Instant.parse("2019-10-10T00:00:00Z"), ZoneOffset.UTC));

    private static final DateTimeFormatter UTC_MILLIS_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .toFormatter();

    private static final String RUBICON = "rubicon";

    @Autowired
    private LineItemService lineItemService;

    @Autowired
    private Clock clock;

    @BeforeClass
    public static void setUpInner() throws IOException {
        // given
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/planner-plan"))
                .withQueryParam("instanceId", equalTo("localhost"))
                .withQueryParam("region", equalTo("local"))
                .withQueryParam("vendor", equalTo("local"))
                .withBasicAuth("username", "password")
                .withHeader("pg-trx-id", new AnythingPattern())
                .withHeader("pg-sim-timestamp", equalTo(UTC_MILLIS_FORMATTER.format(NOW)))
                .willReturn(aResponse()
                        .withBody(IntegrationTest.jsonFrom("deals/simulation/test-planner-plan-response-1.json"))));

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/planner-register"))
                .withBasicAuth("username", "password")
                .withHeader("pg-trx-id", new AnythingPattern())
                .withHeader("pg-sim-timestamp", equalTo(NOW.toString()))
                .withRequestBody(equalToJson(IntegrationTest.jsonFrom(
                        "deals/simulation/test-planner-register-request-1.json")))
                .willReturn(aResponse()));
    }

    @Test
    public void openrtb2AuctionShouldRespondWithDealBids() throws IOException, JSONException, InterruptedException {
        // given
        given(SPEC)
                .header("pg-sim-timestamp", NOW.plusSeconds(0).toString())
                .when()
                .post("/pbs-admin/e2eAdmin/planner/fetchLineItems");

        TimeUnit.SECONDS.sleep(1); // no way to check that planner response handling is complete

        given(SPEC)
                .when()
                .header("pg-sim-timestamp", NOW.plusSeconds(1).toString())
                .post("/pbs-admin/e2eAdmin/advancePlans");

        awaitForLineItemMetadata(NOW.plusSeconds(1));

        given(SPEC)
                .when()
                .body(IntegrationTest.jsonFrom("deals/simulation/test-bid-rates.json"))
                .post("/pbs-admin/e2eAdmin/bidRate");

        final Response beforePlansUpdateResponse = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("User-Agent", "userAgent")
                .header("X-Forwarded-For", "185.199.110.153")
                .header("pg-sim-timestamp", NOW.plusSeconds(2).toString())
                // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT"}}
                .cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIn19")
                .body(IntegrationTest.jsonFrom("deals/simulation/test-auction-request.json"))
                .post("/openrtb2/auction");

        assertResponse("deals/simulation/test-auction-response-1.json", beforePlansUpdateResponse,
                singletonList(RUBICON));

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/delivery-stats-progress"))
                .withBasicAuth("username", "password")
                .withHeader("pg-sim-timestamp", equalTo(UTC_MILLIS_FORMATTER.format(NOW.plusSeconds(3))))
                .withHeader("pg-trx-id", new AnythingPattern())
                .willReturn(aResponse()));

        given(SPEC)
                .when()
                .header("pg-sim-timestamp", NOW.plusSeconds(3).toString())
                .post("/pbs-admin/e2eAdmin/dealstats/report");

        // update plans for now date = 2019-10-10T00:15:00Z - making lineItem1 inactive due to absence of active plan
        given(SPEC)
                .when()
                .header("pg-sim-timestamp", NOW.plusMinutes(15).toString())
                .post("/pbs-admin/e2eAdmin/advancePlans");

        final Response afterPlansUpdateResponse = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("User-Agent", "userAgent")
                .header("X-Forwarded-For", "185.199.110.153")
                .header("pg-sim-timestamp", NOW.plusMinutes(16).toString())
                // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT"}}
                .cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIn19")
                .body(IntegrationTest.jsonFrom("deals/simulation/test-auction-request.json"))
                .post("/openrtb2/auction");

        assertResponse("deals/simulation/test-auction-response-2.json", afterPlansUpdateResponse,
                singletonList(RUBICON));

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/delivery-stats-progress"))
                .withBasicAuth("username", "password")
                .withHeader("pg-sim-timestamp", equalTo(UTC_MILLIS_FORMATTER.format(NOW.plusMinutes(17))))
                .withHeader("pg-trx-id", new AnythingPattern())
                .willReturn(aResponse()));

        given(SPEC)
                .when()
                .header("pg-sim-timestamp", NOW.plusMinutes(17).toString())
                .post("/pbs-admin/e2eAdmin/dealstats/report");

        assertDeliveryStatsProgressRequests(
                "deals/simulation/test-delivery-stats-progress-request-1.json",
                "deals/simulation/test-delivery-stats-progress-request-2.json");

        given(SPEC)
                .header("pg-sim-timestamp", NOW.toString())
                .when()
                .post("/pbs-admin/e2eAdmin/planner/register");
    }

    private void awaitForLineItemMetadata(ZonedDateTime now) {
        await().atMost(20, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> lineItemService.accountHasDeals("2001", now));
    }

    /**
     * Timestamps in response are always generated anew.
     * This comparator allows to just verify they are present and parsable.
     */
    private static CustomComparator openrtbDeepDebugTimeComparator() {
        final ValueMatcher<Object> timeValueMatcher = (actual, expected) -> {
            try {
                return mapper.readValue("\"" + actual.toString() + "\"", ZonedDateTime.class) != null;
            } catch (IOException e) {
                return false;
            }
        };

        final ArrayValueMatcher<Object> arrayValueMatcher = new ArrayValueMatcher<>(new CustomComparator(
                JSONCompareMode.NON_EXTENSIBLE,
                new Customization("ext.debug.trace.deals[*].time", timeValueMatcher)));

        final List<Customization> arrayValueMatchers = IntStream.range(1, 5)
                .mapToObj(i -> new Customization("ext.debug.trace.lineitems.lineItem" + i,
                        new ArrayValueMatcher<>(new CustomComparator(
                                JSONCompareMode.NON_EXTENSIBLE,
                                new Customization("ext.debug.trace.lineitems.lineItem" + i + "[*].time",
                                        timeValueMatcher)))))
                .collect(Collectors.toList());

        arrayValueMatchers.add(new Customization("ext.debug.trace.deals", arrayValueMatcher));

        return new CustomComparator(JSONCompareMode.NON_EXTENSIBLE,
                arrayValueMatchers.toArray(new Customization[arrayValueMatchers.size()]));
    }

    private void assertResponse(String expectedResponsePath, Response response, List<String> bidders)
            throws IOException, JSONException {
        final String expectedAuctionResponse = withTemporalFields(IntegrationTest.openrtbAuctionResponseFrom(
                expectedResponsePath, response, bidders));
        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), openrtbDeepDebugTimeComparator());
    }

    public static void assertDeliveryStatsProgressRequests(String path1, String path2)
            throws IOException, JSONException {
        final String firstReportRequest = IntegrationTest.jsonFrom(path1);
        final String secondReportRequest = IntegrationTest.jsonFrom(path2);

        await().atMost(20, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() ->
                verify(() -> WIRE_MOCK_RULE.verify(2, postRequestedFor(urlPathEqualTo("/delivery-stats-progress")))));

        final List<LoggedRequest> loggedRequests =
                WIRE_MOCK_RULE.findAll(postRequestedFor(urlPathEqualTo("/delivery-stats-progress")));

        JSONAssert.assertEquals(firstReportRequest, loggedRequests.get(0).getBodyAsString(), JSONCompareMode.LENIENT);
        JSONAssert.assertEquals(secondReportRequest, loggedRequests.get(1).getBodyAsString(), JSONCompareMode.LENIENT);
    }

    private String withTemporalFields(String auctionResponse) {
        final ZonedDateTime dateTime = ZonedDateTime.now(clock);

        return auctionResponse
                .replaceAll("\"?\\{\\{ userdow }}\"?", Integer.toString(
                        dateTime.getDayOfWeek().get(WeekFields.SUNDAY_START.dayOfWeek())))
                .replaceAll("\"?\\{\\{ userhour }}\"?", Integer.toString(dateTime.getHour()));
    }

    private static boolean verify(Runnable verify) {
        try {
            verify.run();
            return true;
        } catch (VerificationException e) {
            return false;
        }
    }
}
