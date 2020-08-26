package org.prebid.server.auction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtAppPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtBidderConfig;
import org.prebid.server.proto.openrtb.ext.request.ExtBidderConfigFpd;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidBidderConfig;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.request.Targeting;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class FpdResolverTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private FpdResolver fpdResolver;

    @Before
    public void setUp() {
        fpdResolver = new FpdResolver(jacksonMapper);
    }

    @Test
    public void resolveUserShouldOverrideFpdFieldsFromFpdUser() {
        // given
        final User originUser = User.builder()
                .id("id")
                .buyeruid("buyeruid")
                .yob(1)
                .gender("gender")
                .language("language")
                .keywords("keywords")
                .customdata("customdata")
                .geo(Geo.builder().country("country").build())
                .data(Collections.singletonList(Data.of("id", null, null, null)))
                .build();

        final User fpdUser = User.builder()
                .id("fpdid")
                .buyeruid("fpdbuyeruid")
                .yob(2)
                .gender("fpdgender")
                .language("fpdlanguage")
                .keywords("fpdkeywords")
                .customdata("fpdcustomdata")
                .geo(Geo.builder().country("fpdcountry").build())
                .data(Collections.singletonList(Data.of("fpdid", null, null, null)))
                .build();

        // when
        final User resultUser = fpdResolver.resolveUser(originUser, fpdUser);

        // then
        assertThat(resultUser).isEqualTo(User.builder()
                .id("id")
                .buyeruid("buyeruid")
                .yob(2)
                .gender("fpdgender")
                .language("language")
                .keywords("fpdkeywords")
                .customdata("customdata")
                .geo(Geo.builder().country("fpdcountry").build())
                .data(Collections.singletonList(Data.of("id", null, null, null)))
                .build());
    }

    @Test
    public void resolveUserShouldReturnOriginUserIfFpdUserIsNull() {
        assertThat(fpdResolver.resolveUser(User.builder().id("origin").build(), null))
                .isEqualTo(User.builder().id("origin").build());
    }

    @Test
    public void resolveUserShouldReturnFpdUserIfOriginUserIsNull() {
        assertThat(fpdResolver.resolveUser(null, User.builder().id("fpd").build()))
                .isEqualTo(User.builder().id("fpd").build());
    }

    @Test
    public void resolveUserShouldMergeExtDataAttributesAndReplaceWithFpdPriority() {
        // given
        final User originUser = User.builder()
                .ext(ExtUser.builder().consent("consent").data(mapper.createObjectNode()
                        .put("originAttr", "originValue")
                        .put("replaceAttr", "originValue2")).build())
                .build();

        final User fpdUser = User.builder()
                .ext(ExtUser.builder().consent("fpdConsent").data(mapper.createObjectNode()
                        .put("fpdAttr", "fpdValue")
                        .put("replaceAttr", "fpdValue2")).build())
                .build();

        // when
        final User resultUser = fpdResolver.resolveUser(originUser, fpdUser);

        // then
        assertThat(resultUser).isEqualTo(User.builder()
                .ext(ExtUser.builder().consent("consent").data(mapper.createObjectNode()
                        .put("fpdAttr", "fpdValue")
                        .put("replaceAttr", "fpdValue2")
                        .put("originAttr", "originValue"))
                        .build())
                .build());
    }

    @Test
    public void resolveUserShouldAddExtDataAttributesIfOriginDoesNotHaveExtData() {
        // given
        final User originUser = User.builder()
                .build();

        final User fpdUser = User.builder()
                .ext(ExtUser.builder().data(mapper.createObjectNode()
                        .put("fpdAttr", "fpdValue")
                        .put("replaceAttr", "fpdValue2")).build())
                .build();

        // when
        final User resultUser = fpdResolver.resolveUser(originUser, fpdUser);

        // then
        assertThat(resultUser).isEqualTo(User.builder()
                .ext(ExtUser.builder().data(mapper.createObjectNode()
                        .put("fpdAttr", "fpdValue")
                        .put("replaceAttr", "fpdValue2"))
                        .build())
                .build());
    }

    @Test
    public void resolveUserShouldNotChangeOriginExtDataIfFPDDoesNotHaveExt() {
        // given
        final User originUser = User.builder()
                .ext(ExtUser.builder().data(mapper.createObjectNode()
                        .put("originAttr", "originValue")
                        .put("replaceAttr", "originValue2")).build())
                .build();

        final User fpdUser = User.builder()
                .build();

        // when
        final User resultUser = fpdResolver.resolveUser(originUser, fpdUser);

        // then
        assertThat(resultUser).isEqualTo(User.builder()
                .ext(ExtUser.builder().data(mapper.createObjectNode()
                        .put("originAttr", "originValue")
                        .put("replaceAttr", "originValue2"))
                        .build())
                .build());
    }

    @Test
    public void resolveAppShouldOverrideFpdFieldsFromFpdApp() {
        // given
        final App originApp = App.builder()
                .id("originId")
                .name("originName")
                .bundle("originBundle")
                .domain("originDomain")
                .storeurl("originStoreUrl")
                .cat(Collections.singletonList("originCat"))
                .sectioncat(Collections.singletonList("originSectionCat"))
                .pagecat(Collections.singletonList("originPageCat"))
                .ver("originVer")
                .privacypolicy(1)
                .paid(1)
                .publisher(Publisher.builder().id("originId").build())
                .content(Content.builder().language("originLan").build())
                .keywords("originKeywords")
                .build();

        final App fpdApp = App.builder()
                .id("fpdId")
                .name("fpdName")
                .bundle("fpdBundle")
                .domain("fpdDomain")
                .storeurl("fpdStoreUrl")
                .cat(Collections.singletonList("fpdCat"))
                .sectioncat(Collections.singletonList("fpdSectionCat"))
                .pagecat(Collections.singletonList("fpdPageCat"))
                .ver("fpdVer")
                .privacypolicy(2)
                .paid(2)
                .publisher(Publisher.builder().id("fpdId").build())
                .content(Content.builder().language("fpdLan").build())
                .keywords("fpdKeywords")
                .build();
        // when
        final App resultApp = fpdResolver.resolveApp(originApp, fpdApp);

        // then
        assertThat(resultApp)
                .isEqualTo(App.builder()
                        .id("fpdId")
                        .name("fpdName")
                        .bundle("fpdBundle")
                        .domain("fpdDomain")
                        .storeurl("fpdStoreUrl")
                        .cat(Collections.singletonList("fpdCat"))
                        .sectioncat(Collections.singletonList("fpdSectionCat"))
                        .pagecat(Collections.singletonList("fpdPageCat"))
                        .ver("originVer")
                        .privacypolicy(2)
                        .paid(1)
                        .publisher(Publisher.builder().id("fpdId").build())
                        .content(Content.builder().language("fpdLan").build())
                        .keywords("fpdKeywords")
                        .build());
    }

    @Test
    public void resolveAppShouldReturnOriginAppIfFpdAppIsNull() {
        assertThat(fpdResolver.resolveApp(App.builder().id("origin").build(), null))
                .isEqualTo(App.builder().id("origin").build());
    }

    @Test
    public void resolveAppShouldReturnFpdAppIfOriginAppIsNull() {
        assertThat(fpdResolver.resolveApp(null, App.builder().id("fpd").build()))
                .isEqualTo(App.builder().id("fpd").build());
    }

    @Test
    public void resolveAppShouldMergeExtDataAttributesAndReplaceWithFpdPriority() {
        // given
        final App originApp = App.builder()
                .ext(ExtApp.of(ExtAppPrebid.of("a", "b"), mapper.createObjectNode()
                        .put("originAttr", "originValue")
                        .put("replaceAttr", "originValue2")))
                .build();

        final App fpdApp = App.builder()
                .ext(ExtApp.of(null, mapper.createObjectNode()
                        .put("fpdAttr", "fpdValue")
                        .put("replaceAttr", "fpdValue2")))
                .build();

        // when
        final App resultApp = fpdResolver.resolveApp(originApp, fpdApp);

        // then
        assertThat(resultApp).isEqualTo(App.builder()
                .ext(ExtApp.of(ExtAppPrebid.of("a", "b"), mapper.createObjectNode()
                        .put("fpdAttr", "fpdValue")
                        .put("replaceAttr", "fpdValue2")
                        .put("originAttr", "originValue")))
                .build());
    }

    @Test
    public void resolveSite() {
        // given
        final Site originSite = Site.builder()
                .id("originId")
                .name("originName")
                .ref("originRef")
                .search("originSearch")
                .domain("originDomain")
                .cat(Collections.singletonList("originCat"))
                .page("originPage")
                .sectioncat(Collections.singletonList("originSectionCat"))
                .pagecat(Collections.singletonList("originPageCat"))
                .mobile(1)
                .privacypolicy(1)
                .publisher(Publisher.builder().id("originId").build())
                .content(Content.builder().language("originLan").build())
                .keywords("originKeywords")
                .build();

        final Site fpdSite = Site.builder()
                .id("fpdId")
                .name("fpdName")
                .ref("fpdRef")
                .search("fpdSearch")
                .domain("fpdDomain")
                .cat(Collections.singletonList("fpdCat"))
                .page("fpdPage")
                .sectioncat(Collections.singletonList("fpdSectionCat"))
                .pagecat(Collections.singletonList("fpdPageCat"))
                .mobile(2)
                .privacypolicy(2)
                .publisher(Publisher.builder().id("fpdId").build())
                .content(Content.builder().language("fpdLan").build())
                .keywords("fpdKeywords")
                .build();

        // when
        final Site resultSite = fpdResolver.resolveSite(originSite, fpdSite);

        // then
        assertThat(resultSite).isEqualTo(
                Site.builder()
                        .id("fpdId")
                        .name("fpdName")
                        .ref("fpdRef")
                        .search("fpdSearch")
                        .domain("fpdDomain")
                        .cat(Collections.singletonList("fpdCat"))
                        .page("fpdPage")
                        .sectioncat(Collections.singletonList("fpdSectionCat"))
                        .pagecat(Collections.singletonList("fpdPageCat"))
                        .mobile(2)
                        .privacypolicy(2)
                        .publisher(Publisher.builder().id("fpdId").build())
                        .content(Content.builder().language("fpdLan").build())
                        .keywords("fpdKeywords")
                        .build());
    }

    @Test
    public void resolveSiteShouldReturnOriginSiteIfFpdSiteIsNull() {
        assertThat(fpdResolver.resolveApp(App.builder().id("origin").build(), null))
                .isEqualTo(App.builder().id("origin").build());
    }

    @Test
    public void resolveSiteShouldReturnFpdSiteIfOriginSiteIsNull() {
        assertThat(fpdResolver.resolveSite(null, Site.builder().id("fpd").build()))
                .isEqualTo(Site.builder().id("fpd").build());
    }

    @Test
    public void resolveSiteShouldMergeExtDataAttributesAndReplaceWithFpdPriority() {
        // given
        final Site originSite = Site.builder()
                .ext(ExtSite.of(1, mapper.createObjectNode()
                        .put("originAttr", "originValue")
                        .put("replaceAttr", "originValue2")))
                .build();

        final Site fpdSite = Site.builder()
                .ext(ExtSite.of(null, mapper.createObjectNode()
                        .put("fpdAttr", "fpdValue")
                        .put("replaceAttr", "fpdValue2")))
                .build();

        // when
        final Site resultSite = fpdResolver.resolveSite(originSite, fpdSite);

        // then
        assertThat(resultSite).isEqualTo(Site.builder()
                .ext(ExtSite.of(1, mapper.createObjectNode()
                        .put("fpdAttr", "fpdValue")
                        .put("replaceAttr", "fpdValue2")
                        .put("originAttr", "originValue")))
                .build());
    }

    @Test
    public void resolveImpExtShouldNotUpdateExtImpIfTargetingIsNull() {
        // given
        final ObjectNode extImp = mapper.createObjectNode();

        // when
        final ObjectNode result = fpdResolver.resolveImpExt(extImp, null);

        // then
        assertThat(result).isSameAs(extImp);
    }

    @Test
    public void resolveImpExtShouldNotUpdateExtImpIfTargetingHasNotAttributesToMergeAfterCleaning() {
        // given
        final ObjectNode extImp = mapper.createObjectNode();
        final ObjectNode targeting = mapper.createObjectNode()
                .put("site", "site").put("user", "user").put("app", "app").put("bidders", "bidders");

        // when
        final ObjectNode result = fpdResolver.resolveImpExt(extImp, targeting);

        // then
        assertThat(result).isSameAs(extImp);
    }

    @Test
    public void resolveImpExtShouldMergeExtImpContextDataWithFpdPriority() {
        // given
        final ObjectNode extImp = mapper.createObjectNode().set("context", mapper.createObjectNode()
                .set("data", mapper.createObjectNode().put("replacedAttr", "originValue")
                        .put("originAttr", "originValue")));
        final ObjectNode targeting = mapper.createObjectNode()
                .put("site", "site").put("replacedAttr", "fpdValue").put("fpdAttr", "fpdValue2");

        // when
        final ObjectNode result = fpdResolver.resolveImpExt(extImp, targeting);

        // then
        assertThat(result).isEqualTo(mapper.createObjectNode().set("context", mapper.createObjectNode()
                .set("data", mapper.createObjectNode().put("replacedAttr", "fpdValue")
                        .put("originAttr", "originValue").put("fpdAttr", "fpdValue2"))));
    }

    @Test
    public void resolveImpExtShouldCreateExtImpContextDataIfExtImpIsNull() {
        // given
        final ObjectNode targeting = mapper.createObjectNode()
                .put("site", "site").put("replacedAttr", "fpdValue").put("fpdAttr", "fpdValue2");

        // when
        final ObjectNode result = fpdResolver.resolveImpExt(null, targeting);

        // then
        assertThat(result).isEqualTo(mapper.createObjectNode().set("context", mapper.createObjectNode()
                .set("data", mapper.createObjectNode().put("replacedAttr", "fpdValue").put("fpdAttr", "fpdValue2"))));
    }

    @Test
    public void resolveImpExtShouldCreateExtImpContextDataIfExtImpContextIsNullAndKeepOtherFields() {
        // given
        final ObjectNode extImp = mapper.createObjectNode().set("prebid", mapper.createObjectNode());
        final ObjectNode targeting = mapper.createObjectNode()
                .put("site", "site").put("replacedAttr", "fpdValue").put("fpdAttr", "fpdValue2");

        // when
        final ObjectNode result = fpdResolver.resolveImpExt(extImp, targeting);

        // then
        final ObjectNode expectedResult = mapper.createObjectNode().set("prebid", mapper.createObjectNode());
        assertThat(result).isEqualTo(expectedResult.set("context", mapper.createObjectNode()
                .set("data", mapper.createObjectNode().put("replacedAttr", "fpdValue")
                        .put("fpdAttr", "fpdValue2"))));
    }

    @Test
    public void resolveImpExtShouldCreateExtImpContextDataIfExtImpContextDataIsNullAndKeepOtherFields() {
        // given
        final ObjectNode extImp = mapper.createObjectNode().set("prebid", mapper.createObjectNode());
        extImp.set("context", mapper.createObjectNode().put("keywords", "keywords"));
        final ObjectNode targeting = mapper.createObjectNode()
                .put("site", "site").put("replacedAttr", "fpdValue").put("fpdAttr", "fpdValue2");

        // when
        final ObjectNode result = fpdResolver.resolveImpExt(extImp, targeting);

        // then
        final ObjectNode expectedResult = mapper.createObjectNode().set("prebid", mapper.createObjectNode());
        final ObjectNode context = mapper.createObjectNode().put("keywords", "keywords");
        context.set("data", mapper.createObjectNode().put("replacedAttr", "fpdValue")
                .put("fpdAttr", "fpdValue2"));
        expectedResult.set("context", context);
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void resolveBidRequestExtShouldReturnSameExtIfTargetingIsNull() {
        // given
        final ExtRequest givenExtRequest = ExtRequest.of(null);

        // when
        final ExtRequest result = fpdResolver.resolveBidRequestExt(givenExtRequest, null);

        // then
        assertThat(result).isSameAs(givenExtRequest);
    }

    @Test
    public void resolveBidRequestExtShouldMergeBidders() {
        // given
        final ExtRequest givenExtRequest = ExtRequest.of(ExtRequestPrebid.builder()
                .data(ExtRequestPrebidData.of(Arrays.asList("rubicon", "appnexus"))).build());

        // when
        final ExtRequest result = fpdResolver.resolveBidRequestExt(givenExtRequest,
                Targeting.of(Arrays.asList("rubicon", "adform"), null, null));

        // then
        assertThat(result.getPrebid().getData().getBidders()).contains("rubicon", "appnexus", "adform");
    }

    @Test
    public void resolveBidRequestExtShouldAddBiddersIfExtIsNull() {
        // when
        final ExtRequest result = fpdResolver.resolveBidRequestExt(null,
                Targeting.of(Arrays.asList("rubicon", "adform"), null, null));

        // then
        assertThat(result.getPrebid().getData().getBidders()).contains("rubicon", "adform");
    }

    @Test
    public void resolveBidRequestExtShouldAddBiddersIfExtPrebidIsNull() {
        // given
        final ExtRequest givenExtRequest = ExtRequest.of(null);

        // when
        final ExtRequest result = fpdResolver.resolveBidRequestExt(givenExtRequest,
                Targeting.of(Arrays.asList("rubicon", "adform"), null, null));

        // then
        assertThat(result.getPrebid().getData().getBidders()).contains("rubicon", "adform");
    }

    @Test
    public void resolveBidRequestExtShouldAddBiddersIfExtPrebidDataIsNullKeepingOtherValues() {
        // given
        final ExtRequest givenExtRequest = ExtRequest.of(ExtRequestPrebid.builder().debug(1).build());

        // when
        final ExtRequest result = fpdResolver.resolveBidRequestExt(givenExtRequest,
                Targeting.of(Arrays.asList("rubicon", "adform"), null, null));

        // then
        assertThat(result).isEqualTo(ExtRequest.of(ExtRequestPrebid.builder().debug(1)
                .data(ExtRequestPrebidData.of(Arrays.asList("rubicon", "adform"))).build()));
    }

    @Test
    public void resolveBidRequestExtShouldAddBiddersIfExtPrebidDataBiddersIsNull() {
        // given
        final ExtRequest givenExtRequest = ExtRequest.of(ExtRequestPrebid.builder().debug(1)
                .data(ExtRequestPrebidData.of(null)).build());

        // when
        final ExtRequest result = fpdResolver.resolveBidRequestExt(givenExtRequest,
                Targeting.of(Arrays.asList("rubicon", "adform"), null, null));

        // then
        assertThat(result).isEqualTo(ExtRequest.of(ExtRequestPrebid.builder().debug(1)
                .data(ExtRequestPrebidData.of(Arrays.asList("rubicon", "adform"))).build()));
    }

    @Test
    public void resolveBidRequestExtShouldAddBidderConfig() {
        // given
        final ExtRequest givenExtRequest = ExtRequest.of(null);
        final Targeting targeting = Targeting.of(Collections.emptyList(),
                mapper.valueToTree(Site.builder().id("id").build()),
                mapper.valueToTree(User.builder().id("id").build()));

        // when
        final ExtRequest result = fpdResolver.resolveBidRequestExt(givenExtRequest, targeting);

        // then
        assertThat(result).isEqualTo(ExtRequest.of(ExtRequestPrebid.builder()
                .bidderconfig(Collections.singletonList(ExtRequestPrebidBidderConfig.of(
                        Collections.singletonList("*"), ExtBidderConfig.of(ExtBidderConfigFpd.of(
                                Site.builder().id("id").build(), null, User.builder().id("id").build()))))).build()));
    }

    @Test
    public void resolveBidRequestExtShouldNotAddBidderConfigWhenUserAndSiteIsNull() {
        // given
        final ExtRequest givenExtRequest = ExtRequest.of(null);
        final Targeting targeting = Targeting.of(Collections.emptyList(), null, null);

        // when
        final ExtRequest result = fpdResolver.resolveBidRequestExt(givenExtRequest, targeting);

        // then
        assertThat(result).isEqualTo(ExtRequest.of(null));
    }

    @Test
    public void resolveBidRequestExtShoulUpdateBidderConfigAndData() {
        // given
        final ExtRequest givenExtRequest = ExtRequest.of(null);
        final Targeting targeting = Targeting.of(Arrays.asList("rubicon", "adform"),
                mapper.valueToTree(Site.builder().id("id").build()),
                mapper.valueToTree(User.builder().id("id").build()));

        // when
        final ExtRequest result = fpdResolver.resolveBidRequestExt(givenExtRequest, targeting);

        // then
        assertThat(result).isEqualTo(ExtRequest.of(ExtRequestPrebid.builder()
                .data(ExtRequestPrebidData.of(Arrays.asList("rubicon", "adform")))
                .bidderconfig(Collections.singletonList(ExtRequestPrebidBidderConfig.of(
                        Collections.singletonList("*"), ExtBidderConfig.of(ExtBidderConfigFpd.of(
                                Site.builder().id("id").build(), null, User.builder().id("id").build()))))).build()));
    }
}
