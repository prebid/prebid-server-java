package org.prebid.server.deals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Segment;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.deals.model.TxnLog;
import org.prebid.server.deals.targeting.TargetingDefinition;
import org.prebid.server.deals.targeting.interpret.And;
import org.prebid.server.deals.targeting.interpret.DomainMetricAwareExpression;
import org.prebid.server.deals.targeting.interpret.InIntegers;
import org.prebid.server.deals.targeting.interpret.InStrings;
import org.prebid.server.deals.targeting.interpret.IntersectsIntegers;
import org.prebid.server.deals.targeting.interpret.IntersectsSizes;
import org.prebid.server.deals.targeting.interpret.IntersectsStrings;
import org.prebid.server.deals.targeting.interpret.Matches;
import org.prebid.server.deals.targeting.interpret.Not;
import org.prebid.server.deals.targeting.interpret.Or;
import org.prebid.server.deals.targeting.interpret.Within;
import org.prebid.server.deals.targeting.model.GeoRegion;
import org.prebid.server.deals.targeting.model.Size;
import org.prebid.server.deals.targeting.syntax.TargetingCategory;
import org.prebid.server.deals.targeting.syntax.TargetingCategory.Type;
import org.prebid.server.exception.TargetingSyntaxException;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtGeo;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.io.IOException;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TargetingServiceTest extends VertxTest {

    private TargetingService targetingService;

    @Before
    public void setUp() {
        targetingService = new TargetingService(jacksonMapper);
    }

    @Test
    public void parseTargetingDefinitionShouldReturnValidExpression() throws IOException {
        // when
        final TargetingDefinition definition = targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-valid-targeting-definition.json"), "lineItemId");

        // then
        assertThat(definition).isNotNull().isEqualTo(TargetingDefinition.of(
                new And(asList(
                        new IntersectsSizes(category(Type.size), asList(Size.of(300, 250), Size.of(400, 200))),
                        new IntersectsStrings(category(Type.mediaType), asList("banner", "video")),
                        new Or(asList(
                                new DomainMetricAwareExpression(new Matches(category(Type.domain), "*nba.com*"),
                                        "lineItemId"),
                                new DomainMetricAwareExpression(new Matches(category(Type.domain), "nba.com*"),
                                        "lineItemId"),
                                new DomainMetricAwareExpression(
                                        new InStrings(category(Type.domain), asList("nba.com", "cnn.com")),
                                        "lineItemId")
                        )),
                        new Or(asList(
                                new Matches(category(Type.referrer), "*sports*"),
                                new Matches(category(Type.referrer), "http://nba.com/lalakers*"),
                                new InStrings(category(Type.referrer),
                                        asList("http://cnn.com/culture", "http://cnn.com/weather"))
                        )),
                        new Or(asList(
                                new Matches(category(Type.appBundle), "*com.google.calendar*"),
                                new Matches(category(Type.appBundle), "com.google.calendar*"),
                                new InStrings(category(Type.appBundle),
                                        asList("com.google.calendar", "com.tmz"))
                        )),
                        new Or(asList(
                                new Matches(category(Type.adslot), "*/home/top*"),
                                new Matches(category(Type.adslot), "/home/top*"),
                                new InStrings(category(Type.adslot), asList("/home/top", "/home/bottom"))
                        )),
                        new InStrings(category(Type.deviceGeoExt, "vendor.attribute"),
                                asList("device_geo_ext_value1", "device_geo_ext_value2")),
                        new InStrings(category(Type.deviceGeoExt, "vendor.nested.attribute"),
                                asList("device_geo_ext_nested_value1", "device_geo_ext_nested_value2")),
                        new InStrings(category(Type.deviceExt, "vendor.attribute"),
                                asList("device_ext_value1", "device_ext_value2")),
                        new InStrings(category(Type.deviceExt, "vendor.nested.attribute"),
                                asList("device_ext_nested_value1", "device_ext_nested_value2")),
                        new InIntegers(category(Type.pagePosition), asList(1, 3)),
                        new Within(category(Type.location), GeoRegion.of(123.456f, 789.123f, 10.0f)),
                        new Or(asList(
                                new InIntegers(category(Type.bidderParam, "rubicon.siteId"), asList(123, 321)),
                                new IntersectsIntegers(category(Type.bidderParam, "rubicon.siteId"), asList(123, 321))
                        )),
                        new Or(asList(
                                new Matches(category(Type.bidderParam, "appnexus.placementName"), "*somePlacement*"),
                                new Matches(category(Type.bidderParam, "appnexus.placementName"), "somePlacement*"),
                                new InStrings(category(Type.bidderParam, "appnexus.placementName"),
                                        asList("somePlacement1", "somePlacement2")),
                                new IntersectsStrings(category(Type.bidderParam, "appnexus.placementName"),
                                        asList("somePlacement1", "somePlacement2"))
                        )),
                        new Or(asList(
                                new IntersectsStrings(
                                        category(Type.userSegment, "rubicon"), asList("123", "234", "345")),
                                new IntersectsStrings(
                                        category(Type.userSegment, "bluekai"), asList("123", "234", "345"))
                        )),
                        new Or(asList(
                                new InIntegers(category(Type.userFirstPartyData, "someId"), asList(123, 321)),
                                new IntersectsIntegers(category(Type.userFirstPartyData, "someId"), asList(123, 321))
                        )),
                        new Or(asList(
                                new Matches(category(Type.userFirstPartyData, "sport"), "*hockey*"),
                                new Matches(category(Type.userFirstPartyData, "sport"), "hockey*"),
                                new InStrings(category(Type.userFirstPartyData, "sport"), asList("hockey", "soccer")),
                                new IntersectsStrings(
                                        category(Type.userFirstPartyData, "sport"), asList("hockey", "soccer"))
                        )),
                        new Or(asList(
                                new InIntegers(category(Type.siteFirstPartyData, "someId"), asList(123, 321)),
                                new IntersectsIntegers(category(Type.siteFirstPartyData, "someId"), asList(123, 321))
                        )),
                        new Or(asList(
                                new Matches(category(Type.siteFirstPartyData, "sport"), "*hockey*"),
                                new Matches(category(Type.siteFirstPartyData, "sport"), "hockey*"),
                                new InStrings(category(Type.siteFirstPartyData, "sport"), asList("hockey", "soccer")),
                                new IntersectsStrings(
                                        category(Type.siteFirstPartyData, "sport"), asList("hockey", "soccer"))
                        )),
                        new InIntegers(category(Type.dow), asList(5, 6)),
                        new InIntegers(category(Type.hour), asList(10, 11, 12, 13, 14))
                ))));
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenTopLevelFieldIsNonObject() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-non-object.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Expected array, got NUMBER");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenTopLevelObjectHasMultipleFields() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-multiple-fields.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Expected only one element in the object, got 2");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenBooleanOperatorArgumentHasMultipleFields() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-multiple-fields-boolean-args.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Expected only one element in the object, got 2");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenFieldIsUnknown() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-unknown-field.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Expected either boolean operator or targeting category, got aaa");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenAndWithNonArray() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-and-with-non-array.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Expected array, got OBJECT");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenNotWithNonObject() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-not-with-non-object.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Expected object, got ARRAY");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenCategoryWithNonObject() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-category-non-object.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Expected object, got NUMBER");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenFunctionHasMultipleFields() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-multiple-fields-function.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Expected only one element in the object, got 2");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenUnknownFunction() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-unknown-function.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Expected matching function, got $abc");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenCategoryWithIncompatibleFunction() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-category-incompatible-function.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Expected $intersects matching function, got $in");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenIntersectsNonArray() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-intersects-non-array.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Expected array, got OBJECT");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenIntersectsSizesWithNonObjects() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-intersects-sizes-non-objects.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Expected object, got NUMBER");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenIntersectsSizesWithNonReadableSize() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-intersects-sizes-non-readable-size.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessageStartingWith("Exception occurred while parsing size: "
                        + "Cannot deserialize value of type `java.lang.Integer`");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenIntersectsSizesWithEmptySize() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-intersects-sizes-empty-size.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Height and width in size definition could not be null or missing");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenIntersectsStringsWithNonString() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-intersects-strings-non-string.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Expected string, got NUMBER");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenIntersectsStringsWithEmptyString() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-intersects-strings-empty.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("String value could not be empty");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenUnknownStringFunction() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-unknown-string-function.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Expected matching function, got $abc");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenCategoryWithIncompatibleStringFunction() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-category-incompatible-string-function.json"),
                null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Expected one of $matches, $in matching functions, got $intersects");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenMatchesWithNonString() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-matches-non-string.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Expected string, got NUMBER");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenMatchesWithEmptyString() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-matches-empty.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("String value could not be empty");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenInIntegersWithNonInteger() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-in-integers-non-integer.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Expected integer, got STRING");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenCategoryWithIncompatibleGeoFunction() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-category-incompatible-geo-function.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Expected $within matching function, got $intersects");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenWithinWithNonObject() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-within-non-object.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Expected object, got ARRAY");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenWithinWithNonReadableGeoRegion() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-within-non-readable-georegion.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessageStartingWith("Exception occurred while parsing geo region: "
                        + "Cannot deserialize value of type `java.lang.Float`");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenWithinWithEmptyGeoRegion() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-within-empty-georegion.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Lat, lon and radiusMiles in geo region definition could not be null or missing");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenCategoryWithIncompatibleSegmentFunction() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom(
                        "targeting/test-invalid-targeting-definition-category-incompatible-segment-function.json"),
                null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Expected $intersects matching function, got $in");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenUnknownTypedFunction() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-unknown-typed-function.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Expected matching function, got $abc");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenCategoryWithIncompatibleTypedFunction() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-category-incompatible-typed-function.json"),
                null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Expected one of $matches, $in, $intersects matching functions, got $within");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenTypedFunctionWithIncompatibleType() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-typed-function-incompatible-type.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Expected integer or string, got BOOLEAN");
    }

    @Test
    public void parseTargetingDefinitionShouldFailWhenTypedFunctionWithMixedTypes() {
        assertThatThrownBy(() -> targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-invalid-targeting-definition-typed-function-mixed-types.json"), null))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Expected integer, got STRING");
    }

    @Test
    public void matchesTargetingShouldReturnTrue() {
        // given
        final TargetingDefinition targetingDefinition = TargetingDefinition.of(
                new And(asList(
                        new IntersectsSizes(category(Type.size), asList(Size.of(300, 250), Size.of(400, 200))),
                        new IntersectsStrings(category(Type.mediaType), asList("banner", "video")),
                        new DomainMetricAwareExpression(new Matches(category(Type.domain), "*nba.com*"), "lineItemId"),
                        new InIntegers(category(Type.pagePosition), asList(1, 3)),
                        new Within(category(Type.location), GeoRegion.of(50.424744f, 30.506435f, 10.0f)),
                        new InIntegers(category(Type.bidderParam, "rubicon.siteId"), asList(123, 321)),
                        new IntersectsStrings(category(Type.userSegment, "rubicon"), asList("123", "234", "345")),
                        new IntersectsIntegers(category(Type.userFirstPartyData, "someId"), asList(123, 321)))));

        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder()
                        .domain("lakers.nba.com")
                        .build())
                .device(Device.builder()
                        .geo(Geo.builder()
                                .lat(50.432069f)
                                .lon(30.516455f)
                                .build())
                        .build())
                .user(User.builder()
                        .data(asList(
                                Data.builder()
                                        .id("rubicon")
                                        .segment(asList(
                                                Segment.builder().id("234").build(),
                                                Segment.builder().id("567").build()))
                                        .build(),
                                Data.builder()
                                        .id("bluekai")
                                        .segment(asList(
                                                Segment.builder().id("789").build(),
                                                Segment.builder().id("890").build()))
                                        .build()))
                        .ext(ExtUser.builder().data(mapper.valueToTree(singletonMap("someId", asList(123, 456))))
                                .build())
                        .build())
                .build();

        final TxnLog txnLog = TxnLog.create();
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .txnLog(txnLog)
                .build();

        final Imp imp = Imp.builder()
                .banner(Banner.builder()
                        .format(asList(
                                Format.builder().w(300).h(500).build(),
                                Format.builder().w(300).h(250).build(),
                                Format.builder().w(400).h(500).build()))
                        .pos(3)
                        .build())
                .ext(mapper.createObjectNode()
                        .set("prebid", mapper.createObjectNode()
                                .set("bidder", mapper.valueToTree(
                                        singletonMap("rubicon", singletonMap("siteId", 123))))))
                .build();

        // when and then
        assertThat(targetingService.matchesTargeting(auctionContext, imp, targetingDefinition)).isTrue();
        assertThat(txnLog.lineItemsMatchedDomainTargeting()).containsOnly("lineItemId");
    }

    @Test
    public void matchesTargetingShouldReturnTrueForNotInIntegers() throws IOException {
        // given
        final TargetingDefinition targetingDefinition = targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-not-in-integers-definition.json"), "lineItemId");

        final BidRequest bidRequest = BidRequest.builder()
                .build();

        final TxnLog txnLog = TxnLog.create();
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .txnLog(txnLog)
                .build();

        final Imp imp = Imp.builder()
                .ext(mapper.createObjectNode()
                        .set("prebid", mapper.createObjectNode()
                                .set("bidder", mapper.valueToTree(
                                        singletonMap("rubicon", singletonMap("siteId", 123))))))
                .build();

        // when and then
        assertThat(targetingService.matchesTargeting(auctionContext, imp, targetingDefinition)).isTrue();
    }

    @Test
    public void matchesTargetingShouldReturnTrueForNotInStrings() throws IOException {
        // given
        final TargetingDefinition targetingDefinition = targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-not-in-strings-definition.json"), "lineItemId");

        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder()
                        .domain("lakers.nba.com")
                        .build())
                .build();

        final TxnLog txnLog = TxnLog.create();
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .txnLog(txnLog)
                .build();

        final Imp imp = Imp.builder()
                .build();

        // when and then
        assertThat(targetingService.matchesTargeting(auctionContext, imp, targetingDefinition)).isTrue();
    }

    @Test
    public void matchesTargetingShouldReturnTrueForNotIntersectsInteger() throws IOException {
        final TargetingDefinition targetingDefinition = targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-not-intersects-integer-definition.json"), "lineItemId");

        final BidRequest bidRequest = BidRequest.builder()
                .user(User.builder()
                        .ext(ExtUser.builder().data(mapper.valueToTree(singletonMap("someId", asList(123, 456))))
                                .build())
                        .build())
                .build();

        final TxnLog txnLog = TxnLog.create();
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .txnLog(txnLog)
                .build();

        final Imp imp = Imp.builder().build();

        // when and then
        assertThat(targetingService.matchesTargeting(auctionContext, imp, targetingDefinition)).isTrue();
    }

    @Test
    public void matchesTargetingShouldReturnTrueForNotIntersectsSizes() throws IOException {
        // given
        final TargetingDefinition targetingDefinition = targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-not-intersects-sizes-definition.json"), "lineItemId");

        final BidRequest bidRequest = BidRequest.builder().build();

        final TxnLog txnLog = TxnLog.create();
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .txnLog(txnLog)
                .build();

        final Imp imp = Imp.builder()
                .banner(Banner.builder()
                        .format(asList(
                                Format.builder().w(300).h(500).build(),
                                Format.builder().w(300).h(250).build(),
                                Format.builder().w(400).h(500).build()))
                        .build())
                .build();

        // when and then
        assertThat(targetingService.matchesTargeting(auctionContext, imp, targetingDefinition)).isTrue();
    }

    @Test
    public void matchesTargetingShouldReturnTrueForNotWithin() throws IOException {
        // given
        final TargetingDefinition targetingDefinition = targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-not-within-definition.json"), "lineItemId");

        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .geo(Geo.builder()
                                .lat(50.432069f)
                                .lon(30.516455f)
                                .build())
                        .build())
                .build();

        final TxnLog txnLog = TxnLog.create();
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .txnLog(txnLog)
                .build();

        final Imp imp = Imp.builder()
                .build();

        // when and then
        assertThat(targetingService.matchesTargeting(auctionContext, imp, targetingDefinition)).isTrue();
    }

    @Test
    public void matchesTargetingShouldReturnTrueForNotFalseAnd() throws IOException {
        // given
        final TargetingDefinition targetingDefinition = targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-not-and-definition.json"), "lineItemId");

        final BidRequest bidRequest = BidRequest.builder().build();

        final TxnLog txnLog = TxnLog.create();
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .txnLog(txnLog)
                .build();

        final Imp imp = Imp.builder()
                .banner(Banner.builder()
                        .format(asList(
                                Format.builder().w(300).h(500).build(),
                                Format.builder().w(300).h(250).build(),
                                Format.builder().w(400).h(500).build()))
                        .build())
                .build();

        // when and then
        assertThat(targetingService.matchesTargeting(auctionContext, imp, targetingDefinition)).isTrue();
    }

    @Test
    public void matchesTargetingShouldReturnTrueForNotMatches() throws IOException {
        // given
        final TargetingDefinition targetingDefinition = targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-not-matches-definition.json"), "lineItemId");

        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder()
                        .domain("lakers.uefa.com")
                        .build())
                .build();

        final TxnLog txnLog = TxnLog.create();
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .txnLog(txnLog)
                .build();

        final Imp imp = Imp.builder()

                .build();

        // when and then
        assertThat(targetingService.matchesTargeting(auctionContext, imp, targetingDefinition)).isTrue();
    }

    @Test
    public void matchesTargetingShouldReturnFalseForNotTrueOr() throws IOException {
        // given
        final TargetingDefinition targetingDefinition = targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-not-or-definition.json"), "lineItemId");

        final BidRequest bidRequest = BidRequest.builder().build();

        final TxnLog txnLog = TxnLog.create();
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .txnLog(txnLog)
                .build();

        final Imp imp = Imp.builder()
                .banner(Banner.builder()
                        .format(asList(
                                Format.builder().w(300).h(500).build(),
                                Format.builder().w(300).h(250).build(),
                                Format.builder().w(400).h(500).build()))
                        .build())
                .build();

        // when and then
        assertThat(targetingService.matchesTargeting(auctionContext, imp, targetingDefinition)).isFalse();
    }

    @Test
    public void matchesTargetingShouldReturnTrueForDeviceExt() throws IOException {
        // given
        final TargetingDefinition targetingDefinition = targetingService.parseTargetingDefinition(
                jsonFrom("targeting/test-device-targeting.json"), "lineItemId");

        final ExtGeo extGeo = ExtGeo.of();
        extGeo.addProperty("netacuity", mapper.createObjectNode().set("country", new TextNode("us")));
        final ExtDevice extDevice = ExtDevice.empty();
        extDevice.addProperty("deviceatlas", mapper.createObjectNode().set("browser", new TextNode("Chrome")));
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device
                        .builder()
                        .geo(Geo.builder().ext(extGeo)
                                .build())
                        .ext(extDevice)
                        .build()).build();

        final TxnLog txnLog = TxnLog.create();

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .txnLog(txnLog)
                .build();

        final Imp imp = Imp.builder().build();

        // when and then
        assertThat(targetingService.matchesTargeting(auctionContext, imp, targetingDefinition)).isTrue();
    }

    @Test
    public void matchesTargetingShouldReturnFalseForNotInIntegers() {
        // given
        final TargetingDefinition targetingDefinition = TargetingDefinition.of(
                new Not(new InIntegers(category(Type.bidderParam, "rubicon.siteId"), asList(123, 778))));

        final BidRequest bidRequest = BidRequest.builder()
                .build();

        final TxnLog txnLog = TxnLog.create();
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .txnLog(txnLog)
                .build();

        final Imp imp = Imp.builder()
                .ext(mapper.createObjectNode()
                        .set("prebid", mapper.createObjectNode()
                                .set("bidder", mapper.valueToTree(
                                        singletonMap("rubicon", singletonMap("siteId", 123))))))
                .build();

        // when and then
        assertThat(targetingService.matchesTargeting(auctionContext, imp, targetingDefinition)).isFalse();
    }

    @Test
    public void matchesTargetingShouldReturnFalseForNotInStrings() {
        // given
        final TargetingDefinition targetingDefinition = TargetingDefinition.of(
                new Not(new InStrings(category(Type.domain), asList("nba.com", "cnn.com"))));

        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder()
                        .domain("nba.com")
                        .build())
                .build();

        final TxnLog txnLog = TxnLog.create();
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .txnLog(txnLog)
                .build();

        final Imp imp = Imp.builder()
                .build();

        // when and then
        assertThat(targetingService.matchesTargeting(auctionContext, imp, targetingDefinition)).isFalse();
    }

    @Test
    public void matchesTargetingShouldReturnFalseForNotIntersectsInteger() {
        final TargetingDefinition targetingDefinition = TargetingDefinition.of(
                new Not(new IntersectsIntegers(category(Type.userFirstPartyData, "someId"), asList(123, 321))));

        final BidRequest bidRequest = BidRequest.builder()
                .user(User.builder()
                        .ext(ExtUser.builder().data(mapper.valueToTree(singletonMap("someId", asList(123, 456))))
                                .build())
                        .build())
                .build();

        final TxnLog txnLog = TxnLog.create();
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .txnLog(txnLog)
                .build();

        final Imp imp = Imp.builder().build();

        // when and then
        assertThat(targetingService.matchesTargeting(auctionContext, imp, targetingDefinition)).isFalse();
    }

    @Test
    public void matchesTargetingShouldReturnFalseForNotIntersectsSizes() {
        // given
        final TargetingDefinition targetingDefinition = TargetingDefinition.of(
                new Not(new IntersectsSizes(category(Type.size), asList(Size.of(300, 250), Size.of(400, 200)))));

        final BidRequest bidRequest = BidRequest.builder().build();

        final TxnLog txnLog = TxnLog.create();
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .txnLog(txnLog)
                .build();

        final Imp imp = Imp.builder()
                .banner(Banner.builder()
                        .format(asList(
                                Format.builder().w(300).h(500).build(),
                                Format.builder().w(300).h(250).build(),
                                Format.builder().w(400).h(500).build()))
                        .build())
                .build();

        // when and then
        assertThat(targetingService.matchesTargeting(auctionContext, imp, targetingDefinition)).isFalse();
    }

    @Test
    public void matchesTargetingShouldReturnFalseForNotWithin() {
        // given
        final TargetingDefinition targetingDefinition = TargetingDefinition.of(
                new Not(new Within(category(Type.location), GeoRegion.of(50.424744f, 30.506435f, 10.0f))));

        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .geo(Geo.builder()
                                .lat(50.432069f)
                                .lon(30.516455f)
                                .build())
                        .build())
                .build();

        final TxnLog txnLog = TxnLog.create();
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .txnLog(txnLog)
                .build();

        final Imp imp = Imp.builder()
                .build();

        // when and then
        assertThat(targetingService.matchesTargeting(auctionContext, imp, targetingDefinition)).isFalse();
    }

    @Test
    public void matchesTargetingShouldReturnFalseForNotTrueAnd() {
        // given
        final TargetingDefinition targetingDefinition = TargetingDefinition.of(
                new Not(new And(asList(
                        new IntersectsSizes(category(Type.size), asList(Size.of(300, 250), Size.of(400, 200))),
                        new IntersectsStrings(category(Type.mediaType), asList("banner", "video"))))));

        final BidRequest bidRequest = BidRequest.builder().build();

        final TxnLog txnLog = TxnLog.create();
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .txnLog(txnLog)
                .build();

        final Imp imp = Imp.builder()
                .banner(Banner.builder()
                        .format(asList(
                                Format.builder().w(300).h(500).build(),
                                Format.builder().w(300).h(250).build(),
                                Format.builder().w(400).h(500).build()))
                        .build())
                .build();

        // when and then
        assertThat(targetingService.matchesTargeting(auctionContext, imp, targetingDefinition)).isFalse();
    }

    @Test
    public void matchesTargetingShouldReturnFalseForNotMatches() {
        // given
        final TargetingDefinition targetingDefinition = TargetingDefinition.of(
                new Not(new Matches(category(Type.domain), "*nba.com*")));

        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder()
                        .domain("lakers.nba.com")
                        .build())
                .build();

        final TxnLog txnLog = TxnLog.create();
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .txnLog(txnLog)
                .build();

        final Imp imp = Imp.builder()

                .build();

        // when and then
        assertThat(targetingService.matchesTargeting(auctionContext, imp, targetingDefinition)).isFalse();
    }

    private static JsonNode jsonFrom(String file) throws IOException {
        return mapper.readTree(TargetingServiceTest.class.getResourceAsStream(file));
    }

    private static TargetingCategory category(Type type) {
        return new TargetingCategory(type);
    }

    private static TargetingCategory category(Type type, String path) {
        return new TargetingCategory(type, path);
    }
}
