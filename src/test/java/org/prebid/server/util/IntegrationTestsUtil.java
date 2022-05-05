package org.prebid.server.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.json.JSONException;
import org.prebid.server.it.IntegrationTest;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.model.Endpoint;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.CustomComparator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public class IntegrationTestsUtil {

    private static final ObjectMapper MAPPER = ObjectMapperProvider.mapper();

    private IntegrationTestsUtil() {
    }

    public static void assertJsonEquals(String file,
                                        Response response,
                                        List<String> bidders,
                                        BiFunction<String, String, String> bidderStaticInfoUpdater,
                                        Function<String, String> staticInfoUpdater,
                                        Customization... customizations) throws IOException, JSONException {

        final List<Customization> fullCustomizations = new ArrayList<>(Arrays.asList(customizations));
        fullCustomizations.add(new Customization("ext.prebid.auctiontimestamp", (o1, o2) -> true));
        fullCustomizations.add(new Customization("ext.responsetimemillis.cache", (o1, o2) -> true));

        String expectedRequest = staticInfoUpdater.apply(jsonFrom(file));
        for (String bidder : bidders) {
            expectedRequest = bidderStaticInfoUpdater.apply(expectedRequest, bidder);
            fullCustomizations.add(new Customization(
                    String.format("ext.responsetimemillis.%s", bidder), (o1, o2) -> true));
        }

        JSONAssert.assertEquals(
                expectedRequest,
                response.asString(),
                new CustomComparator(JSONCompareMode.NON_EXTENSIBLE, fullCustomizations.toArray(new Customization[0])));
    }

    public static void assertJsonEquals(String file,
                                        Response response,
                                        List<String> bidders,
                                        BiFunction<String, String, String> bidderStaticInfoUpdater,
                                        Customization... customizations) throws IOException, JSONException {

        assertJsonEquals(file, response, bidders, bidderStaticInfoUpdater, Function.identity(), customizations);
    }

    public static String replaceBidderRelatedStaticInfo(String json, String bidder, int wiremockPort) {
        return json.replaceAll("\\{\\{ " + bidder + "\\.exchange_uri }}",
                "http://localhost:" + wiremockPort + "/" + bidder + "-exchange");
    }

    public static Response responseFor(String file, Endpoint endpoint, RequestSpecification requestSpecification)
            throws IOException {

        return RestAssured.given(requestSpecification)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                .body(jsonFrom(file))
                .post(endpoint.value());
    }

    public static String jsonFrom(String file) throws IOException {
        // workaround to clear formatting
        return MAPPER.writeValueAsString(MAPPER.readTree(IntegrationTest.class.getResourceAsStream(file)));
    }
}
