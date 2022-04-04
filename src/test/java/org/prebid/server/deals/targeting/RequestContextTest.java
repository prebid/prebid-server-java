package org.prebid.server.deals.targeting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Segment;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.deals.model.TxnLog;
import org.prebid.server.deals.targeting.model.GeoLocation;
import org.prebid.server.deals.targeting.model.LookupResult;
import org.prebid.server.deals.targeting.model.Size;
import org.prebid.server.deals.targeting.syntax.TargetingCategory;
import org.prebid.server.exception.TargetingSyntaxException;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtBidderConfig;
import org.prebid.server.proto.openrtb.ext.request.ExtBidderConfigOrtb;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtGeo;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidBidderConfig;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserTime;

import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RequestContextTest extends VertxTest {

    private TxnLog txnLog;

    @Before
    public void setUp() {
        txnLog = TxnLog.create();
    }

    @Test
    public void lookupStringShouldReturnDomainFromSite() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.domain);
        final RequestContext context = new RequestContext(
                request(r -> r.site(site(s -> s.domain("domain.com")))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category).getValues()).containsExactly("domain.com");
    }

    @Test
    public void lookupStringShouldReturnDomainFromSitePublisher() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.domain);
        final RequestContext context = new RequestContext(
                request(r -> r.site(site(s -> s
                        .publisher(Publisher.builder().domain("domain.com").build())))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category).getValues()).containsExactly("domain.com");
    }

    @Test
    public void lookupStringShouldReturnEmptyResultWhenDomainIsMissing() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.domain);
        final RequestContext context = new RequestContext(
                request(r -> r.site(site(identity()))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category)).isEqualTo(LookupResult.empty());
    }

    @Test
    public void lookupStringShouldReturnEmptyResultForDomainWhenSiteIsMissing() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.domain);
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category)).isEqualTo(LookupResult.empty());
    }

    @Test
    public void lookupStringShouldReturnPublisherDomainFromSitePublisher() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.publisherDomain);
        final RequestContext context =
                new RequestContext(
                        request(r -> r.site(site(s -> s
                                .publisher(Publisher.builder().domain("domain.com").build())))),
                        imp(identity()),
                        txnLog,
                        jacksonMapper);

        // when and then
        assertThat(context.lookupString(category).getValues()).containsExactly("domain.com");
    }

    @Test
    public void lookupStringShouldReturnEmptyResultForPublisherDomainWhenSiteIsMissing() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.publisherDomain);
        final RequestContext context =
                new RequestContext(request(identity()), imp(identity()), txnLog, jacksonMapper);

        // when and then
        assertThat(context.lookupString(category)).isEqualTo(LookupResult.empty());
    }

    @Test
    public void lookupStringShouldReturnReferrer() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.referrer);
        final RequestContext context = new RequestContext(
                request(r -> r.site(site(s -> s.page("https://domain.com/index")))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category).getValues()).containsExactly("https://domain.com/index");
    }

    @Test
    public void lookupStringShouldReturnAppBundle() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.appBundle);
        final RequestContext context = new RequestContext(
                request(r -> r.app(app(a -> a.bundle("com.google.calendar")))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category).getValues()).containsExactly("com.google.calendar");
    }

    @Test
    public void lookupStringShouldReturnEmptyResultWhenBundleIsMissing() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.appBundle);
        final RequestContext context = new RequestContext(
                request(r -> r.app(app(identity()))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category)).isEqualTo(LookupResult.empty());
    }

    @Test
    public void lookupStringShouldReturnEmptyResultWhenAppIsMissing() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.appBundle);
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category)).isEqualTo(LookupResult.empty());
    }

    @Test
    public void lookupStringShouldReturnAdslotFromContextDataPbadslot() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.adslot);
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.ext(mapper.createObjectNode()
                        .set("context", mapper.createObjectNode()
                                .set("data", mapper.createObjectNode()
                                        .put("pbadslot", "/123/456"))))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category).getValues()).containsExactly("/123/456");
    }

    @Test
    public void lookupStringShouldReturnAdslotFromContextDataAdserverAdslot() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.adslot);
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.ext(mapper.createObjectNode()
                        .set("context", mapper.createObjectNode()
                                .set("data", mapper.createObjectNode()
                                        .set("adserver", obj("adslot", "/234/567")))))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category).getValues()).containsExactly("/234/567");
    }

    @Test
    public void lookupStringShouldReturnAdslotFromDataPbadslot() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.adslot);
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.ext(mapper.createObjectNode()
                        .set("data", mapper.createObjectNode().put("pbadslot", "/345/678")))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category).getValues()).containsExactly("/345/678");
    }

    @Test
    public void lookupStringShouldReturnAdslotFromDataAdserverAdslot() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.adslot);
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.ext(mapper.createObjectNode()
                        .set("data", mapper.createObjectNode()
                                .set("adserver", obj("adslot", "/456/789"))))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category).getValues()).containsExactly("/456/789");
    }

    @Test
    public void lookupStringShouldReturnAdslotFromAlternativeAdServerAdSlotPath() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.adslot);
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.ext(obj("context", obj("data", obj("adserver", obj("adslot", "/123/456")))))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category).getValues()).containsExactly("/123/456");
    }

    @Test
    public void lookupStringShouldReturnEmptyResultWhenAdslotIsMissing() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.adslot);
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.ext(obj("context", obj("data", mapper.createObjectNode())))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category)).isEqualTo(LookupResult.empty());
    }

    @Test
    public void lookupStringShouldReturnCountryFromDeviceGeoExtValue() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.deviceGeoExt,
                "vendor.attribute");
        final ExtGeo extGeo = ExtGeo.of();
        extGeo.addProperty("vendor", obj("attribute", "value"));
        final RequestContext context = new RequestContext(
                request(r -> r.device(device(d -> d.geo(geo(g -> g.ext(extGeo)))))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category).getValues()).containsExactly("value");
    }

    @Test
    public void lookupStringShouldReturnRegionFromDeviceGeoExtValue() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.deviceGeoExt,
                "vendor.nested.attribute");
        final ExtGeo extGeo = ExtGeo.of();
        extGeo.addProperty("vendor", obj("nested", obj("attribute", "value")));
        final RequestContext context = new RequestContext(
                request(r -> r.device(device(d -> d.geo(geo(g -> g.ext(extGeo)))))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category).getValues()).containsExactly("value");
    }

    @Test
    public void lookupStringShouldReturnMetroFromDeviceExtValue() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.deviceExt,
                "vendor.attribute");
        final ExtDevice extDevice = ExtDevice.of(null, null);
        extDevice.addProperty("vendor", obj("attribute", "value"));

        final RequestContext context = new RequestContext(
                request(r -> r.device(device(d -> d.ext(extDevice)))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category).getValues()).containsExactly("value");
    }

    @Test
    public void lookupStringShouldReturnMetroFromDeviceExtNestedValue() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.deviceExt,
                "vendor.nested.attribute");
        final ExtDevice extDevice = ExtDevice.of(null, null);
        extDevice.addProperty("vendor", obj("nested", obj("attribute", "value")));
        final RequestContext context = new RequestContext(
                request(r -> r.device(device(d -> d.ext(extDevice)))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category).getValues()).containsExactly("value");
    }

    @Test
    public void lookupStringShouldReturnSimpleBidderParam() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.bidderParam, "rubicon.siteId");
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.ext(obj("prebid", obj("bidder", obj("rubicon", obj("siteId", "123")))))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category).getValues()).containsExactly("123");
    }

    @Test
    public void lookupStringShouldReturnNestedBidderParam() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.bidderParam,
                "rubicon.inv.code");
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.ext(obj("prebid", obj("bidder", obj("rubicon", obj("inv", obj("code", "123"))))))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category).getValues()).containsExactly("123");
    }

    @Test
    public void lookupStringShouldReturnEmptyResultWhenBidderParamIsNotString() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.bidderParam, "rubicon.siteId");
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.ext(obj("rubicon", obj("siteId", mapper.valueToTree(123))))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category)).isEqualTo(LookupResult.empty());
    }

    @Test
    public void lookupStringShouldReturnEmptyResultWhenBidderParamIsMissing() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.bidderParam, "rubicon.siteId");
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.ext(obj("rubicon", "phony"))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category)).isEqualTo(LookupResult.empty());
    }

    @Test
    public void lookupStringShouldReturnEmptyResultWhenImpExtIsMissingForBidderParam() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.bidderParam, "rubicon.siteId");
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category)).isEqualTo(LookupResult.empty());
    }

    @Test
    public void lookupStringShouldReturnSimpleUserFirstPartyDataFromObject() {
        // given
        final TargetingCategory category = new TargetingCategory(
                TargetingCategory.Type.userFirstPartyData, "buyeruid");
        final RequestContext context = new RequestContext(
                request(r -> r.user(user(u -> u.buyeruid("123")))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category).getValues()).containsExactly("123");
    }

    @Test
    public void lookupStringShouldReturnSimpleUserFirstPartyDataFromExt() {
        // given
        final TargetingCategory category = new TargetingCategory(
                TargetingCategory.Type.userFirstPartyData, "sport");
        final ExtUser extUser = ExtUser.builder().data(obj("sport", "hockey")).build();
        final RequestContext context = new RequestContext(
                request(r -> r.user(user(u -> u.ext(extUser)))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category).getValues()).containsExactly("hockey");
    }

    @Test
    public void lookupStringShouldReturnUserFirstPartyDataFromExtWhenObjectAttributeTypeIsNotString() {
        // given
        final TargetingCategory category = new TargetingCategory(
                TargetingCategory.Type.userFirstPartyData, "yob");
        final ExtUser extUser = ExtUser.builder().data(obj("yob", "1900")).build();
        final RequestContext context = new RequestContext(
                request(r -> r.user(user(u -> u.yob(1800).ext(extUser)))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category).getValues()).containsExactly("1900");
    }

    @Test
    public void lookupStringShouldReturnNestedUserFirstPartyData() {
        // given
        final TargetingCategory category = new TargetingCategory(
                TargetingCategory.Type.userFirstPartyData, "section.sport");
        final ExtUser extUser = ExtUser.builder().data(obj("section", obj("sport", "hockey"))).build();
        final RequestContext context = new RequestContext(
                request(r -> r.user(user(u -> u.ext(extUser)))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category).getValues()).containsExactly("hockey");
    }

    @Test
    public void lookupStringShouldReturnEmptyResultWhenUserFirstPartyDataIsNotString() {
        // given
        final TargetingCategory category = new TargetingCategory(
                TargetingCategory.Type.userFirstPartyData, "sport");
        final ExtUser extUser = ExtUser.builder().data(obj("sport", mapper.valueToTree(123))).build();
        final RequestContext context = new RequestContext(
                request(r -> r.user(user(u -> u.ext(extUser)))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category)).isEqualTo(LookupResult.empty());
    }

    @Test
    public void lookupStringShouldReturnEmptyResultWhenUserExtIsMissingForUserFirstPartyData() {
        // given
        final TargetingCategory category = new TargetingCategory(
                TargetingCategory.Type.userFirstPartyData, "sport");
        final RequestContext context = new RequestContext(
                request(r -> r.user(user(identity()))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category)).isEqualTo(LookupResult.empty());
    }

    @Test
    public void lookupStringShouldReturnEmptyResultWhenUserIsMissingForUserFirstPartyData() {
        // given
        final TargetingCategory category = new TargetingCategory(
                TargetingCategory.Type.userFirstPartyData, "sport");
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category)).isEqualTo(LookupResult.empty());
    }

    @Test
    public void lookupStringShouldReturnSiteFirstPartyDataFromImpExt() {
        // given
        final TargetingCategory category = new TargetingCategory(
                TargetingCategory.Type.siteFirstPartyData, "section.sport");
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.ext(obj("context", obj("data", obj("section", obj("sport", "hockey")))))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category).getValues()).containsExactly("hockey");
    }

    @Test
    public void lookupStringShouldReturnSiteFirstPartyDataFromSiteExt() {
        // given
        final TargetingCategory category = new TargetingCategory(
                TargetingCategory.Type.siteFirstPartyData, "section.sport");
        final ExtSite extSite = ExtSite.of(null, obj("section", obj("sport", "hockey")));
        final RequestContext context = new RequestContext(
                request(r -> r.site(site(s -> s.ext(extSite)))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category).getValues()).containsExactly("hockey");
    }

    @Test
    public void lookupStringShouldReturnSiteFirstPartyDataFromAppExt() {
        // given
        final TargetingCategory category = new TargetingCategory(
                TargetingCategory.Type.siteFirstPartyData, "section.sport");
        final ExtApp extApp = ExtApp.of(null, obj("section", obj("sport", "hockey")));
        final RequestContext context = new RequestContext(
                request(r -> r.app(app(a -> a.ext(extApp)))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category).getValues()).containsExactly("hockey");
    }

    @Test
    public void lookupStringShouldReturnSiteFirstPartyDataFromExt() {
        // given
        final TargetingCategory category = new TargetingCategory(
                TargetingCategory.Type.siteFirstPartyData, "section.sport");
        final ExtRequest ext = ExtRequest.of(ExtRequestPrebid.builder()
                .bidderconfig(singletonList(
                        ExtRequestPrebidBidderConfig.of(emptyList(), ExtBidderConfig.of(null, ExtBidderConfigOrtb.of(
                                obj("ext", obj("data", obj("section", obj("sport", "hockey")))), null, null)))))
                .build());//ExtApp.of(null, obj("section", obj("sport", "hockey")));
        final RequestContext context = new RequestContext(
                request(r -> r.ext(ext)),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupString(category).getValues()).containsExactly("hockey");
    }

    @Test
    public void lookupIntegerShouldReturnDowFromUserExt() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.dow);
        final ExtUser extUser = ExtUser.builder().time(ExtUserTime.of(5, 15)).build();
        final RequestContext context = new RequestContext(
                request(r -> r.user(user(u -> u.ext(extUser)))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupInteger(category).getValues()).containsExactly(5);
    }

    @Test
    public void lookupIntegerShouldReturnHourFromExt() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.hour);
        final ExtUser extUser = ExtUser.builder().time(ExtUserTime.of(5, 15)).build();
        final RequestContext context = new RequestContext(
                request(r -> r.user(user(u -> u.ext(extUser)))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupInteger(category).getValues()).containsExactly(15);
    }

    @Test
    public void lookupIntegerShouldReturnBidderParam() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.bidderParam, "rubicon.siteId");
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.ext(obj("prebid", obj("bidder", obj("rubicon", obj("siteId", mapper.valueToTree(123))))))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupInteger(category).getValues()).containsExactly(123);
    }

    @Test
    public void lookupIntegerShouldReturnEmptyResultWhenBidderParamIsNotInteger() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.bidderParam, "rubicon.siteId");
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.ext(obj("rubicon", obj("siteId", mapper.valueToTree(123.456d))))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupInteger(category)).isEqualTo(LookupResult.empty());
    }

    @Test
    public void lookupIntegerShouldReturnEmptyResultWhenBidderParamIsMissing() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.bidderParam, "rubicon.siteId");
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.ext(obj("rubicon", "phony"))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupInteger(category)).isEqualTo(LookupResult.empty());
    }

    @Test
    public void lookupIntegerShouldReturnUserFirstPartyData() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.userFirstPartyData, "sport");
        final ExtUser extUser = ExtUser.builder().data(obj("sport", mapper.valueToTree(123))).build();
        final RequestContext context = new RequestContext(
                request(r -> r.user(user(u -> u.ext(extUser)))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupInteger(category).getValues()).containsExactly(123);
    }

    @Test
    public void lookupIntegerShouldReturnSiteFirstPartyData() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.siteFirstPartyData, "sport");
        final ExtSite extSite = ExtSite.of(null, obj("sport", mapper.valueToTree(123)));
        final RequestContext context = new RequestContext(
                request(r -> r.site(site(s -> s.ext(extSite)))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupInteger(category).getValues()).containsExactly(123);
    }

    @Test
    public void lookupStringsShouldReturnMediaTypeBannerAndVideo() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.mediaType);
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.banner(banner(identity())).video(Video.builder().build())),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupStrings(category).getValues()).containsExactly(asList("banner", "video"));
    }

    @Test
    public void lookupStringsShouldReturnMediaTypeVideoAndNative() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.mediaType);
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.video(Video.builder().build()).xNative(Native.builder().build())),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupStrings(category).getValues()).containsExactly(asList("video", "native"));
    }

    @Test
    public void lookupStringsShouldReturnBidderParam() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.bidderParam, "rubicon.siteId");
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.ext(obj("prebid", obj("bidder",
                        obj("rubicon", obj("siteId", mapper.valueToTree(asList("123", "456")))))))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupStrings(category).getValues()).containsExactly(asList("123", "456"));
    }

    @Test
    public void lookupStringsShouldReturnEmptyResultWhenBidderParamIsNotArray() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.bidderParam, "rubicon.siteId");
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.ext(obj("prebid", obj("bidder",
                        obj("rubicon", obj("siteId", mapper.createObjectNode())))))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupStrings(category)).isEqualTo(LookupResult.empty());
    }

    @Test
    public void lookupStringsShouldReturnListOfSingleStringWhenBidderParamIsString() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.bidderParam, "rubicon.siteId");
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.ext(obj("prebid", obj("bidder",
                        obj("rubicon", obj("siteId", "value")))))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupStrings(category).getValues()).containsExactly(singletonList("value"));
    }

    @Test
    public void lookupStringsShouldReturnOnlyStringsWhenNonStringBidderParamPresent() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.bidderParam, "rubicon.siteId");
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.ext(obj("prebid", obj("bidder",
                        obj("rubicon", obj("siteId", mapper.valueToTree(asList("123", 456)))))))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupStrings(category).getValues()).containsExactly(singletonList("123"));
    }

    @Test
    public void lookupStringsShouldReturnEmptyResultWhenBidderParamIsMissing() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.bidderParam, "rubicon.siteId");
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.ext(obj("prebid", obj("bidder", obj("prebid", obj("bidder",
                        obj("rubicon", "phony"))))))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupStrings(category)).isEqualTo(LookupResult.empty());
    }

    @Test
    public void lookupStringsShouldReturnUserFirstPartyData() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.userFirstPartyData, "sport");
        final ExtUser extUser = ExtUser.builder().data(obj("sport", mapper.valueToTree(asList("123", "456")))).build();
        final RequestContext context = new RequestContext(
                request(r -> r.user(user(u -> u.ext(extUser)))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupStrings(category).getValues()).containsExactly(asList("123", "456"));
    }

    @Test
    public void lookupStringsShouldReturnSiteFirstPartyData() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.siteFirstPartyData, "sport");
        final ExtSite extSite = ExtSite.of(null, obj("sport", mapper.valueToTree(asList("123", "456"))));
        final RequestContext context = new RequestContext(
                request(r -> r.site(site(s -> s.ext(extSite)))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupStrings(category).getValues()).containsExactly(asList("123", "456"));
    }

    @Test
    public void lookupStringsShouldReturnSegmentsWithDesiredSource() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.userSegment, "rubicon");
        final RequestContext context = new RequestContext(
                request(r -> r.user(user(u -> u.data(asList(
                        data(d -> d.id("rubicon").segment(asList(segment(s -> s.id("1")), segment(s -> s.id("2"))))),
                        data(d -> d.id("bluekai").segment(
                                asList(segment(s -> s.id("3")), segment(s -> s.id("4")))))))))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupStrings(category).getValues()).containsExactly(asList("1", "2"));
    }

    @Test
    public void lookupStringsShouldReturnEmptyResultWhenDesiredSourceIsMissing() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.userSegment, "rubicon");
        final RequestContext context = new RequestContext(
                request(r -> r.user(user(u -> u.data(singletonList(
                        data(d -> d.id("bluekai").segment(
                                asList(segment(s -> s.id("3")), segment(s -> s.id("4")))))))))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupStrings(category)).isEqualTo(LookupResult.empty());
    }

    @Test
    public void lookupStringsShouldSkipSegmentsWithoutIds() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.userSegment, "rubicon");
        final RequestContext context = new RequestContext(
                request(r -> r.user(user(u -> u.data(singletonList(
                        data(d -> d.id("rubicon").segment(asList(segment(s -> s.id("1")), segment(identity()))))))))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupStrings(category).getValues()).containsExactly(singletonList("1"));
    }

    @Test
    public void lookupStringsShouldReturnEmptyResultWhenSegmentsAreMissing() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.userSegment, "rubicon");
        final RequestContext context = new RequestContext(
                request(r -> r.user(user(u -> u.data(singletonList(data(d -> d.id("rubicon"))))))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupStrings(category)).isEqualTo(LookupResult.empty());
    }

    @Test
    public void lookupStringsShouldTolerateMissingSource() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.userSegment, "rubicon");
        final RequestContext context = new RequestContext(
                request(r -> r.user(user(u -> u.data(singletonList(data(identity())))))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupStrings(category)).isEqualTo(LookupResult.empty());
    }

    @Test
    public void lookupStringsShouldReturnEmptyResultWhenDataIsMissing() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.userSegment, "rubicon");
        final RequestContext context = new RequestContext(
                request(r -> r.user(user(identity()))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupStrings(category)).isEqualTo(LookupResult.empty());
    }

    @Test
    public void lookupStringsShouldReturnEmptyResultWhenUserIsMissing() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.userSegment, "rubicon");
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupStrings(category)).isEqualTo(LookupResult.empty());
    }

    @Test
    public void lookupIntegersShouldReturnBidderParam() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.bidderParam, "rubicon.siteId");
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.ext(obj("prebid", obj("bidder",
                        obj("rubicon", obj("siteId", mapper.valueToTree(asList(123, 456)))))))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupIntegers(category).getValues()).containsExactly(asList(123, 456));
    }

    @Test
    public void lookupIntegersShouldReturnEmptyResultWhenBidderParamIsNotArray() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.bidderParam, "rubicon.siteId");
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.ext(obj("prebid", obj("bidder",
                        obj("rubicon", obj("siteId", mapper.createObjectNode())))))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupIntegers(category)).isEqualTo(LookupResult.empty());
    }

    @Test
    public void lookupIntegersShouldReturnListOfSingleIntegerWhenBidderParamIsInteger() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.bidderParam, "rubicon.siteId");
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.ext(obj("prebid", obj("bidder",
                        obj("rubicon", obj("siteId", 123)))))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupIntegers(category).getValues()).containsExactly(singletonList(123));
    }

    @Test
    public void lookupIntegersShouldReturnOnlyIntegersWhenNonIntegerBidderParamPresent() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.bidderParam, "rubicon.siteId");
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.ext(obj("prebid", obj("bidder",
                        obj("rubicon", obj("siteId", mapper.valueToTree(asList(123, "456")))))))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupIntegers(category).getValues()).containsExactly(singletonList(123));
    }

    @Test
    public void lookupIntegersShouldReturnEmptyResultWhenBidderParamIsMissing() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.bidderParam, "rubicon.siteId");
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.ext(obj("prebid", obj("bidder",
                        obj("rubicon", "phony"))))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupIntegers(category)).isEqualTo(LookupResult.empty());
    }

    @Test
    public void lookupIntegersShouldReturnUserFirstPartyData() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.userFirstPartyData, "sport");
        final ExtUser extUser = ExtUser.builder().data(obj("sport", mapper.valueToTree(asList(123, 456)))).build();
        final RequestContext context = new RequestContext(
                request(r -> r.user(user(u -> u.ext(extUser)))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupIntegers(category).getValues()).containsExactly(asList(123, 456));
    }

    @Test
    public void lookupIntegersShouldReturnSiteFirstPartyData() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.siteFirstPartyData, "sport");
        final ExtSite extSite = ExtSite.of(null, obj("sport", mapper.valueToTree(asList(123, 456))));
        final RequestContext context = new RequestContext(
                request(r -> r.site(site(s -> s.ext(extSite)))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupIntegers(category).getValues()).containsExactly(asList(123, 456));
    }

    @Test
    public void lookupSizesShouldReturnSizes() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.size);
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.banner(banner(b -> b.format(asList(format(300, 250), format(400, 300)))))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupSizes(category).getValues())
                .containsExactly(asList(Size.of(300, 250), Size.of(400, 300)));
    }

    @Test
    public void lookupSizesShouldReturnEmptyResultWhenFormatIsMissing() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.size);
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(i -> i.banner(banner(identity()))),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupSizes(category)).isEqualTo(LookupResult.empty());
    }

    @Test
    public void lookupSizesShouldReturnEmptyResultWhenBannerIsMissing() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.size);
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupSizes(category)).isEqualTo(LookupResult.empty());
    }

    @Test
    public void lookupSizesShouldThrowExceptionWhenUnexpectedCategory() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.domain);
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThatThrownBy(() -> context.lookupSizes(category))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Unexpected category for fetching sizes for: domain");
    }

    @Test
    public void lookupGeoLocationShouldReturnLocation() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.location);
        final RequestContext context = new RequestContext(
                request(r -> r.device(device(d -> d.geo(geo(g -> g.lat(50f).lon(60f)))))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupGeoLocation(category)).isEqualTo(GeoLocation.of(50f, 60f));
    }

    @Test
    public void lookupGeoLocationShouldReturnNullWhenLonIsMissing() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.location);
        final RequestContext context = new RequestContext(
                request(r -> r.device(device(d -> d.geo(geo(g -> g.lat(50f)))))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupGeoLocation(category)).isNull();
    }

    @Test
    public void lookupGeoLocationShouldReturnNullWhenLatIsMissing() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.location);
        final RequestContext context = new RequestContext(
                request(r -> r.device(device(d -> d.geo(geo(g -> g.lon(60f)))))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupGeoLocation(category)).isNull();
    }

    @Test
    public void lookupGeoLocationShouldReturnNullWhenGeoIsMissing() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.location);
        final RequestContext context = new RequestContext(
                request(r -> r.device(device(identity()))),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupGeoLocation(category)).isNull();
    }

    @Test
    public void lookupGeoLocationShouldReturnNullWhenDeviceIsMissing() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.location);
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThat(context.lookupGeoLocation(category)).isNull();
    }

    @Test
    public void lookupGeoLocationShouldThrowExceptionWhenUnexpectedCategory() {
        // given
        final TargetingCategory category = new TargetingCategory(TargetingCategory.Type.domain);
        final RequestContext context = new RequestContext(
                request(identity()),
                imp(identity()),
                txnLog,
                jacksonMapper);

        // when and then
        assertThatThrownBy(() -> context.lookupGeoLocation(category))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("Unexpected category for fetching geo location for: domain");
    }

    private static BidRequest request(Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> customizer) {
        return customizer.apply(BidRequest.builder()).build();
    }

    private static Site site(Function<Site.SiteBuilder, Site.SiteBuilder> customizer) {
        return customizer.apply(Site.builder()).build();
    }

    private static App app(Function<App.AppBuilder, App.AppBuilder> customizer) {
        return customizer.apply(App.builder()).build();
    }

    private static Device device(Function<Device.DeviceBuilder, Device.DeviceBuilder> customizer) {
        return customizer.apply(Device.builder()).build();
    }

    private static Geo geo(Function<Geo.GeoBuilder, Geo.GeoBuilder> customizer) {
        return customizer.apply(Geo.builder()).build();
    }

    private static User user(Function<User.UserBuilder, User.UserBuilder> customizer) {
        return customizer.apply(User.builder()).build();
    }

    private static Data data(Function<Data.DataBuilder, Data.DataBuilder> customizer) {
        return customizer.apply(Data.builder()).build();
    }

    private static Segment segment(Function<Segment.SegmentBuilder, Segment.SegmentBuilder> customizer) {
        return customizer.apply(Segment.builder()).build();
    }

    private static Imp imp(Function<Imp.ImpBuilder, Imp.ImpBuilder> customizer) {
        return customizer.apply(Imp.builder()).build();
    }

    private static Banner banner(Function<Banner.BannerBuilder, Banner.BannerBuilder> customizer) {
        return customizer.apply(Banner.builder()).build();
    }

    private static Format format(int w, int h) {
        return Format.builder().w(w).h(h).build();
    }

    private static ObjectNode obj(String field, String value) {
        return mapper.createObjectNode().put(field, value);
    }

    private static ObjectNode obj(String field, Integer value) {
        return mapper.createObjectNode().put(field, value);
    }

    private static ObjectNode obj(String field, JsonNode value) {
        return mapper.createObjectNode().set(field, value);
    }
}
