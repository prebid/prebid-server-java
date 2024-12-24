package org.prebid.server.auction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Dooh;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtAppPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtDooh;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class FpdResolverTest extends VertxTest {

    private FpdResolver target;

    @BeforeEach
    public void setUp() {
        target = new FpdResolver(jacksonMapper, new JsonMerger(jacksonMapper));
    }

    @Test
    public void resolveUserShouldOverrideFpdFieldsFromFpdUser() {
        // given
        final User originUser = User.builder()
                .id("id")
                .buyeruid("buyeruid")
                .yob(1)
                .gender("gender")
                .keywords("keywords")
                .customdata("customdata")
                .geo(Geo.builder().country("country").build())
                .data(Collections.singletonList(Data.builder().id("id").build()))
                .build();

        final User fpdUser = User.builder()
                .id("fpdid")
                .buyeruid("fpdbuyeruid")
                .yob(2)
                .gender("fpdgender")
                .keywords("fpdkeywords")
                .customdata("fpdcustomdata")
                .geo(Geo.builder().country("fpdcountry").build())
                .data(Collections.singletonList(Data.builder().id("fpdid").build()))
                .build();

        // when
        final User resultUser = target.resolveUser(originUser, mapper.valueToTree(fpdUser));

        // then
        assertThat(resultUser).isEqualTo(User.builder()
                .id("fpdid")
                .keywords("fpdkeywords")
                .yob(2)
                .gender("fpdgender")
                .buyeruid("fpdbuyeruid")
                .customdata("fpdcustomdata")
                .geo(Geo.builder().country("fpdcountry").build())
                .data(Collections.singletonList(Data.builder().id("fpdid").build()))
                .build());
    }

    @Test
    public void resolveUserShouldReturnOriginUserIfFpdUserIsNull() {
        assertThat(target.resolveUser(User.builder().id("origin").build(), null))
                .isEqualTo(User.builder().id("origin").build());
    }

    @Test
    public void resolveUserShouldReturnFpdUserIfOriginUserIsNull() {
        assertThat(target.resolveUser(null, mapper.valueToTree(User.builder().gender("male").build())))
                .isEqualTo(User.builder().gender("male").build());
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
        final User resultUser = target.resolveUser(originUser, mapper.valueToTree(fpdUser));

        // then
        assertThat(resultUser).isEqualTo(User.builder()
                .ext(ExtUser.builder().data(mapper.createObjectNode()
                                .put("originAttr", "originValue")
                                .put("replaceAttr", "originValue2"))
                        .build())
                .build());
    }

    @Test
    public void resolveUserShouldReturnCopyOfUserExtDataIfFPDUserExtDataIsMissing() {
        // given
        final ObjectNode originExtUserData = mapper.createObjectNode().put("originAttr", "originValue");

        final User originUser = User.builder()
                .ext(ExtUser.builder().data(originExtUserData).build())
                .build();

        final User fpdUser = User.builder()
                .ext(ExtUser.builder().data(null).build())
                .build();

        // when
        final User resultUser = target.resolveUser(originUser, mapper.valueToTree(fpdUser));

        // then
        assertThat(resultUser.getExt().getData() != originExtUserData).isTrue(); // different by reference
        assertThat(resultUser.getExt().getData().equals(originExtUserData)).isTrue(); // but the same by value
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
                .ext(ExtApp.of(ExtAppPrebid.of("originalSource", null), mapper.createObjectNode()))
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
                .ext(ExtApp.of(
                        ExtAppPrebid.of(null, "fpdVersion"),
                        mapper.createObjectNode().put("data", "fpdData")))
                .build();

        // when
        final App resultApp = target.resolveApp(originApp, mapper.valueToTree(fpdApp));

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
                        .publisher(Publisher.builder().id("fpdId").build())
                        .content(Content.builder().language("fpdLan").build())
                        .ver("fpdVer")
                        .privacypolicy(2)
                        .paid(2)
                        .keywords("fpdKeywords")
                        .ext(ExtApp.of(
                                ExtAppPrebid.of("originalSource", "fpdVersion"),
                                mapper.createObjectNode().put("data", "fpdData")))
                        .build());
    }

    @Test
    public void resolveAppShouldReturnOriginAppIfFpdAppIsNull() {
        assertThat(target.resolveApp(App.builder().id("origin").build(), null))
                .isEqualTo(App.builder().id("origin").build());
    }

    @Test
    public void resolveAppShouldReturnFpdAppIfOriginAppIsNull() {
        assertThat(target.resolveApp(null, mapper.valueToTree(App.builder().name("fpd").build())))
                .isEqualTo(App.builder().name("fpd").build());
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
        final App resultApp = target.resolveApp(originApp, mapper.valueToTree(fpdApp));

        // then
        assertThat(resultApp).isEqualTo(App.builder()
                .ext(ExtApp.of(ExtAppPrebid.of("a", "b"), mapper.createObjectNode()
                        .put("fpdAttr", "fpdValue")
                        .put("replaceAttr", "fpdValue2")
                        .put("originAttr", "originValue")))
                .build());
    }

    @Test
    public void resolveSiteShouldOverrideFpdFieldsFromFpdSite() {
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
        final Site resultSite = target.resolveSite(originSite, mapper.valueToTree(fpdSite));

        // then
        assertThat(resultSite).isEqualTo(
                Site.builder()
                        .name("fpdName")
                        .domain("fpdDomain")
                        .cat(Collections.singletonList("fpdCat"))
                        .sectioncat(Collections.singletonList("fpdSectionCat"))
                        .pagecat(Collections.singletonList("fpdPageCat"))
                        .page("fpdPage")
                        .ref("fpdRef")
                        .search("fpdSearch")
                        .keywords("fpdKeywords")
                        .id("fpdId")
                        .content(Content.builder().language("fpdLan").build())
                        .publisher(Publisher.builder().id("fpdId").build())
                        .privacypolicy(2)
                        .mobile(2)
                        .build());
    }

    @Test
    public void resolveSiteShouldReturnOriginSiteIfFpdSiteIsNull() {
        assertThat(target.resolveApp(App.builder().id("origin").build(), null))
                .isEqualTo(App.builder().id("origin").build());
    }

    @Test
    public void resolveSiteShouldReturnFpdSiteIfOriginSiteIsNull() {
        assertThat(target.resolveSite(null, mapper.valueToTree(Site.builder().name("fpd").build())))
                .isEqualTo(Site.builder().name("fpd").build());
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
        final Site resultSite = target.resolveSite(originSite, mapper.valueToTree(fpdSite));

        // then
        assertThat(resultSite).isEqualTo(Site.builder()
                .ext(ExtSite.of(1, mapper.createObjectNode()
                        .put("fpdAttr", "fpdValue")
                        .put("replaceAttr", "fpdValue2")
                        .put("originAttr", "originValue")))
                .build());
    }

    @Test
    public void resolveDoohShouldOverrideFpdFieldsFromFpdDooh() {
        // given
        final Dooh originDooh = Dooh.builder()
                .id("originId")
                .name("originName")
                .domain("originDomain")
                .venuetype(List.of("originVenuetype"))
                .venuetypetax(1)
                .publisher(Publisher.builder().id("originId").build())
                .content(Content.builder().language("originLan").build())
                .keywords("originKeywords")
                .build();

        final Dooh fpdDooh = Dooh.builder()
                .id("fpdId")
                .name("fpdName")
                .domain("fpdDomain")
                .venuetype(List.of("fpdVenuetype"))
                .venuetypetax(2)
                .publisher(Publisher.builder().id("fpdId").build())
                .content(Content.builder().language("fpdLan").build())
                .keywords("fpdKeywords")
                .build();

        // when
        final Dooh resultDooh = target.resolveDooh(originDooh, mapper.valueToTree(fpdDooh));

        // then
        assertThat(resultDooh).isEqualTo(
                Dooh.builder()
                        .id("fpdId")
                        .name("fpdName")
                        .domain("fpdDomain")
                        .venuetype(List.of("fpdVenuetype"))
                        .venuetypetax(2)
                        .publisher(Publisher.builder().id("fpdId").build())
                        .content(Content.builder().language("fpdLan").build())
                        .keywords("fpdKeywords")
                        .build());
    }

    @Test
    public void resolveDoohShouldReturnOriginDoohIfFpdDoohIsNull() {
        assertThat(target.resolveDooh(Dooh.builder().id("origin").build(), null))
                .isEqualTo(Dooh.builder().id("origin").build());
    }

    @Test
    public void resolveDoohShouldReturnFpdDoohIfOriginDoohIsNull() {
        assertThat(target.resolveDooh(null, mapper.valueToTree(Dooh.builder().name("fpd").build())))
                .isEqualTo(Dooh.builder().name("fpd").build());
    }

    @Test
    public void resolveDoohShouldMergeExtDataAttributesAndReplaceWithFpdPriority() {
        // given
        final Dooh originDooh = Dooh.builder()
                .ext(ExtDooh.of(mapper.createObjectNode()
                        .put("originAttr", "originValue")
                        .put("replaceAttr", "originValue2")))
                .build();

        final Dooh fpdDooh = Dooh.builder()
                .ext(ExtDooh.of(mapper.createObjectNode()
                        .put("fpdAttr", "fpdValue")
                        .put("replaceAttr", "fpdValue2")))
                .build();

        // when
        final Dooh resultDooh = target.resolveDooh(originDooh, mapper.valueToTree(fpdDooh));

        // then
        assertThat(resultDooh).isEqualTo(Dooh.builder()
                .ext(ExtDooh.of(mapper.createObjectNode()
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
        final ObjectNode result = target.resolveImpExt(extImp, null);

        // then
        assertThat(result).isSameAs(extImp);
    }

    @Test
    public void resolveImpExtShouldNotUpdateExtImpIfTargetingHasNotAttributesToMergeAfterCleaning() {
        // given
        final ObjectNode extImp = mapper.createObjectNode();
        final ObjectNode targeting = mapper.createObjectNode()
                .put("site", "site")
                .put("user", "user")
                .put("app", "app")
                .put("dooh", "dooh")
                .put("bidders", "bidders");

        // when
        final ObjectNode result = target.resolveImpExt(extImp, targeting);

        // then
        assertThat(result).isSameAs(extImp);
    }

    @Test
    public void resolveImpExtShouldMergeExtImpContextDataWithFpdPriority() {
        // given
        final ObjectNode extImp = mapper.createObjectNode().set("data", mapper.createObjectNode()
                .put("replacedAttr", "originValue")
                .put("originAttr", "originValue"));
        final ObjectNode targeting = mapper.createObjectNode()
                .put("site", "site")
                .put("replacedAttr", "fpdValue")
                .put("fpdAttr", "fpdValue2");

        // when
        final ObjectNode result = target.resolveImpExt(extImp, targeting);

        // then
        assertThat(result).isEqualTo(mapper.createObjectNode()
                .set("data", mapper.createObjectNode().put("replacedAttr", "fpdValue")
                        .put("originAttr", "originValue").put("fpdAttr", "fpdValue2")));
    }

    @Test
    public void resolveImpExtShouldCreateExtImpContextDataIfExtImpIsNull() {
        // given
        final ObjectNode targeting = mapper.createObjectNode()
                .put("site", "site").put("replacedAttr", "fpdValue").put("fpdAttr", "fpdValue2");

        // when
        final ObjectNode result = target.resolveImpExt(null, targeting);

        // then
        assertThat(result).isEqualTo(mapper.createObjectNode()
                .set("data", mapper.createObjectNode().put("replacedAttr", "fpdValue").put("fpdAttr", "fpdValue2")));
    }

    @Test
    public void resolveImpExtShouldCreateExtImpContextDataIfExtImpContextIsNullAndKeepOtherFields() {
        // given
        final ObjectNode extImp = mapper.createObjectNode().put("prebid", 1).put("rubicon", 2);
        final ObjectNode targeting = mapper.createObjectNode()
                .put("site", "site").put("replacedAttr", "fpdValue").put("fpdAttr", "fpdValue2");

        // when
        final ObjectNode result = target.resolveImpExt(extImp, targeting);

        // then
        final ObjectNode expectedResult = mapper.createObjectNode().put("prebid", 1).put("rubicon", 2);
        assertThat(result).isEqualTo(expectedResult
                .set("data", mapper.createObjectNode().put("replacedAttr", "fpdValue")
                        .put("fpdAttr", "fpdValue2")));
    }

    @Test
    public void resolveImpExtShouldCreateExtImpDataIfExtImpDataIsNullAndKeepOtherFields() {
        // given
        final ObjectNode extImp = mapper.createObjectNode().set("prebid", mapper.createObjectNode());
        final ObjectNode targeting = mapper.createObjectNode()
                .put("site", "site").put("replacedAttr", "fpdValue").put("fpdAttr", "fpdValue2");

        // when
        final ObjectNode result = target.resolveImpExt(extImp, targeting);

        // then
        final ObjectNode expectedResult = mapper.createObjectNode().set("prebid", mapper.createObjectNode());
        expectedResult.set("data", mapper.createObjectNode().put("replacedAttr", "fpdValue")
                .put("fpdAttr", "fpdValue2"));
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void resolveImpExtShouldNotSetContextIfContextIsAbsent() {
        // given
        final ObjectNode impExt = mapper.createObjectNode();

        // when
        final ObjectNode result = target.resolveImpExt(impExt, true);

        // then
        assertThat(result.get("context")).isNull();
    }

    @Test
    public void resolveImpExtShouldNotRemoveDataFromContextIfFPDEnabled() {
        // given
        final ObjectNode impExt = mapper.createObjectNode()
                .set("context", mapper.createObjectNode()
                        .set("data", mapper.createObjectNode()
                                .put("attr1", "value1")));

        // when
        final ObjectNode result = target.resolveImpExt(impExt, true);

        // then
        assertThat(result.get("context")).isEqualTo(mapper.createObjectNode()
                .set("data", mapper.createObjectNode()
                        .put("attr1", "value1")));
    }

    @Test
    public void resolveImpExtShouldRemoveDataFromContextIfFPDDisabled() {
        // given
        final ObjectNode impExt = mapper.createObjectNode()
                .set("context", mapper.createObjectNode()
                        .put("keyword", "keyw1")
                        .set("data", mapper.createObjectNode()
                                .put("attr1", "value1")));

        // when
        final ObjectNode result = target.resolveImpExt(impExt, false);

        // then
        assertThat(result.get("context")).isEqualTo(mapper.createObjectNode()
                .put("keyword", "keyw1"));
    }

    @Test
    public void resolveImpExtShouldTolerateNonObjectContextIfFPDDisabled() {
        // given
        final ObjectNode impExt = mapper.createObjectNode()
                .set("context", mapper.createArrayNode().add("value1"));

        // when
        final ObjectNode result = target.resolveImpExt(impExt, false);

        // then
        assertThat(result.get("context")).isEqualTo(mapper.createArrayNode().add("value1"));
    }

    @Test
    public void resolveImpExtShouldNotSetContextIfEmpty() {
        // given
        final ObjectNode impExt = mapper.createObjectNode()
                .set("context", mapper.createObjectNode()
                        .set("data", mapper.createObjectNode()
                                .put("attr1", "value1")));

        // when
        final ObjectNode result = target.resolveImpExt(impExt, false);

        // then
        assertThat(result.get("context")).isNull();
    }

    @Test
    public void resolveImpExtShouldNotSetDataIfDataIsAbsent() {
        // given
        final ObjectNode impExt = mapper.createObjectNode();

        // when
        final ObjectNode result = target.resolveImpExt(impExt, true);

        // then
        assertThat(result.get("data")).isNull();
    }

    @Test
    public void resolveImpExtShouldNotSetDataIfFPDDisabled() {
        // given
        final ObjectNode impExt = mapper.createObjectNode()
                .set("data", mapper.createObjectNode()
                        .put("attr1", "value1"));

        // when
        final ObjectNode result = target.resolveImpExt(impExt, false);

        // then
        assertThat(result.get("data")).isNull();
    }

    @Test
    public void resolveImpExtShouldSetDataFromContextDataIfDataIsAbsent() {
        // given
        final ObjectNode impExt = mapper.createObjectNode()
                .set("context", mapper.createObjectNode()
                        .set("data", mapper.createObjectNode()
                                .put("attr1", "value1")));

        // when
        final ObjectNode result = target.resolveImpExt(impExt, true);

        // then
        assertThat(result.get("data")).isEqualTo(mapper.createObjectNode()
                .put("attr1", "value1"));
    }

    @Test
    public void resolveImpExtShouldMergeDataWithContextDataIfDataIsPresent() {
        // given
        final ObjectNode impExt = mapper.createObjectNode()
                .<ObjectNode>set("context", mapper.createObjectNode()
                        .set("data", mapper.createObjectNode()
                                .put("attr1", "value1")))
                .set("data", mapper.createObjectNode()
                        .put("attr2", "value2"));

        // when
        final ObjectNode result = target.resolveImpExt(impExt, true);

        // then
        assertThat(result.get("data")).isEqualTo(mapper.createObjectNode()
                .put("attr1", "value1")
                .put("attr2", "value2"));
    }

    @Test
    public void resolveImpExtShouldNotChangeDataIfContextDataIsAbsent() {
        // given
        final ObjectNode impExt = mapper.createObjectNode()
                .<ObjectNode>set("context", mapper.createObjectNode())
                .set("data", mapper.createObjectNode()
                        .put("attr2", "value2"));

        // when
        final ObjectNode result = target.resolveImpExt(impExt, true);

        // then
        assertThat(result.get("data")).isEqualTo(mapper.createObjectNode()
                .put("attr2", "value2"));
    }

    @Test
    public void resolveImpExtShouldTolerateNonObjectData() {
        // given
        final ObjectNode impExt = mapper.createObjectNode()
                .<ObjectNode>set("context", mapper.createObjectNode()
                        .set("data", mapper.createObjectNode()
                                .put("attr1", "value1")))
                .set("data", mapper.createArrayNode()
                        .add("attr2"));

        // when
        final ObjectNode result = target.resolveImpExt(impExt, true);

        // then
        assertThat(result.get("data")).isEqualTo(mapper.createArrayNode()
                .add("attr2"));
    }

    @Test
    public void resolveImpExtShouldNotMergeNonObjectContextData() {
        // given
        final ObjectNode impExt = mapper.createObjectNode()
                .<ObjectNode>set("context", mapper.createArrayNode()
                        .add("attr1"))
                .set("data", mapper.createObjectNode()
                        .put("attr2", "value2"));

        // when
        final ObjectNode result = target.resolveImpExt(impExt, true);

        // then
        assertThat(result.get("data")).isEqualTo(mapper.createObjectNode()
                .put("attr2", "value2"));
    }

    @Test
    public void resolveImpExtShouldNotRemoveUninvolvedFields() {
        // given
        final ObjectNode impExt = mapper.createObjectNode()
                .set("all", mapper.createObjectNode()
                        .put("attr1", "value1"));

        // when
        final ObjectNode result = target.resolveImpExt(impExt, true);

        // then
        assertThat(result.get("all")).isEqualTo(mapper.createObjectNode()
                .put("attr1", "value1"));
    }

    @Test
    public void resolveImpExtShouldNotRemoveUninvolvedFieldsIfFPDDisabled() {
        // given
        final ObjectNode impExt = mapper.createObjectNode()
                .set("all", mapper.createObjectNode()
                        .put("attr1", "value1"));

        // when
        final ObjectNode result = target.resolveImpExt(impExt, false);

        // then
        assertThat(result.get("all")).isEqualTo(mapper.createObjectNode()
                .put("attr1", "value1"));
    }

}
