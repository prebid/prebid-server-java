package org.prebid.server.auction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.json.JsonMerger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class OrtbTypesResolverTest extends VertxTest {

    private final OrtbTypesResolver ortbTypesResolver =
            new OrtbTypesResolver(jacksonMapper, new JsonMerger(jacksonMapper));

    @Test
    public void normalizeTargetingShouldNotChangeNodeIfItsTypeIsNotObject() {
        // given
        final JsonNode inputParam = mapper.createArrayNode();

        // when
        ortbTypesResolver.normalizeTargeting(inputParam, new ArrayList<>(), "referer");

        // then
        assertThat(inputParam).isEqualTo(mapper.createArrayNode());
    }

    @Test
    public void normalizeTargetingShouldConvertArrayToFirstElementFieldForUserAndWriteMessage() {
        // given
        final JsonNode inputParam = mapper.createObjectNode().set("user",
                mapper.createObjectNode().set("gender", array("male", "female")));
        final List<String> errors = new ArrayList<>();

        // when
        ortbTypesResolver.normalizeTargeting(inputParam, errors, "referer");

        // then
        assertThat(inputParam).isEqualTo(mapper.createObjectNode().set("user",
                mapper.createObjectNode().put("gender", "male")));
        assertThat(errors).containsOnly("WARNING: Incorrect type for first party data field targeting.user.gender,"
                + " expected is string, but was an array of strings. Converted to string by taking first element "
                + "of array.");
    }

    @Test
    public void normalizeTargetingShouldConvertArrayToCommaSeparatedStringFieldForUserAndWriteMessage() {
        // given
        final JsonNode inputParam = mapper.createObjectNode().set("user",
                mapper.createObjectNode().set("keywords", array("keyword1", "keyword2")));
        final List<String> errors = new ArrayList<>();

        // when
        ortbTypesResolver.normalizeTargeting(inputParam, errors, "referer");

        // then
        assertThat(inputParam).isEqualTo(mapper.createObjectNode().set("user",
                mapper.createObjectNode().put("keywords", "keyword1,keyword2")));
        assertThat(errors).containsOnly("WARNING: Incorrect type for first party data field targeting.user.keywords,"
                + " expected is string, but was an array of strings. Converted to string by separating values with"
                + " comma.");
    }

    @Test
    public void normalizeTargetingShouldRemoveUserIfNull() {
        final JsonNode inputParam = mapper.createObjectNode().set("user", null);
        final List<String> errors = new ArrayList<>();

        // when
        ortbTypesResolver.normalizeTargeting(inputParam, errors, "referer");

        // then
        assertThat(inputParam).isEqualTo(mapper.createObjectNode());
    }

    @Test
    public void normalizeTargetingToCommaSeparatedTextNodeShouldWriteMessageAndRemoveThatField() {
        // given
        final JsonNode inputParam = mapper.createObjectNode().set("user",
                mapper.createObjectNode().set("keywords", mapper.createArrayNode().add("keyword1").add(2)));
        final List<String> errors = new ArrayList<>();

        // when
        ortbTypesResolver.normalizeTargeting(inputParam, errors, "referer");

        // then
        assertThat(inputParam).isEqualTo(mapper.createObjectNode().set("user", mapper.createObjectNode()));
        assertThat(errors).containsOnly("WARNING: Incorrect type for first party data field targeting.user.keywords,"
                + " expected strings, but was `ARRAY of different types`. Failed to convert to correct type.");
    }

    @Test
    public void normalizeTargetingToFirstElementTextNodeShouldWriteMessageAndRemoveField() {
        // given
        final JsonNode inputParam = mapper.createObjectNode()
                .set("user", mapper.createObjectNode().set("gender", new IntNode(1)));
        final List<String> errors = new ArrayList<>();

        // when
        ortbTypesResolver.normalizeTargeting(inputParam, errors, "referer");

        // then
        assertThat(inputParam).isEqualTo(mapper.createObjectNode().set("user", mapper.createObjectNode()));
        assertThat(errors).containsOnly("WARNING: Incorrect type for first party data field targeting.user.gender,"
                + " expected strings, but was `NUMBER`. Failed to convert to correct type.");
    }

    @Test
    public void normalizeTargetingShouldNormalizeFieldsForUser() {
        // given
        final ObjectNode user = mapper.createObjectNode()
                .set("gender", array("gender1", "gender2"));
        user.set("keywords", array("keyword1", "keyword2"));
        final ObjectNode containerNode = mapper.createObjectNode().set("user", user);

        // when
        ortbTypesResolver.normalizeTargeting(containerNode, new ArrayList<>(), "referer");

        // then
        assertThat(containerNode).isEqualTo(mapper.createObjectNode().set("user", mapper.createObjectNode()
                .put("gender", "gender1")
                .put("keywords", "keyword1,keyword2")));
    }

    @Test
    public void normalizeTargetingShouldNormalizeFieldsForAppExceptId() {
        // given
        final ObjectNode app = mapper.createObjectNode();
        app.set("id", array("id1", "id2"));
        app.set("name", array("name1", "name2"));
        app.set("bundle", array("bundle1", "bundle2"));
        app.set("storeurl", array("storeurl1", "storeurl2"));
        app.set("domain", array("domain1", "domain2"));
        app.set("keywords", array("keyword1", "keyword2"));
        final ObjectNode containerNode = mapper.createObjectNode().set("app", app);

        // when
        ortbTypesResolver.normalizeTargeting(containerNode, new ArrayList<>(), "referer");

        // then
        assertThat(containerNode).isEqualTo(mapper.createObjectNode().set("app", mapper.createObjectNode()
                .put("name", "name1")
                .put("bundle", "bundle1")
                .put("storeurl", "storeurl1")
                .put("domain", "domain1")
                .put("keywords", "keyword1,keyword2")
                .set("id", array("id1", "id2"))));
    }

    @Test
    public void normalizeTargetingShouldNormalizeFieldsForSiteExceptId() {
        // given
        final ObjectNode site = mapper.createObjectNode();
        site.set("id", array("id1", "id2"));
        site.set("name", array("name1", "name2"));
        site.set("domain", array("domain1", "domain2"));
        site.set("page", array("page1", "page2"));
        site.set("ref", array("ref1", "ref2"));
        site.set("search", array("search1", "search2"));
        site.set("keywords", array("keyword1", "keyword2"));
        final ObjectNode containerNode = mapper.createObjectNode().set("site", site);

        // when
        ortbTypesResolver.normalizeTargeting(containerNode, new ArrayList<>(), "referer");

        // then
        assertThat(containerNode).isEqualTo(mapper.createObjectNode().set("site", mapper.createObjectNode()
                .put("name", "name1")
                .put("page", "page1")
                .put("ref", "ref1")
                .put("domain", "domain1")
                .put("search", "search1")
                .put("keywords", "keyword1,keyword2")
                .set("id", array("id1", "id2")))
        );
    }

    @Test
    public void normalizeTargetingShouldTolerateIncorrectTypeSiteFieldAndRemoveIt() {
        // given
        final ObjectNode containerNode = mapper.createObjectNode().put("site", "notObjectType");

        // when
        ortbTypesResolver.normalizeTargeting(containerNode, new ArrayList<>(), "referer");

        // then
        assertThat(containerNode).isEqualTo(mapper.createObjectNode());
    }

    @Test
    public void normalizeTargetingShouldTolerateIncorrectTypeAppFieldAndRemoveIt() {
        // given
        final ObjectNode containerNode = mapper.createObjectNode().put("app", "notObjectType");

        // when
        ortbTypesResolver.normalizeTargeting(containerNode, new ArrayList<>(), "referer");

        // then
        assertThat(containerNode).isEqualTo(mapper.createObjectNode());
    }

    @Test
    public void normalizeTargetingShouldTolerateIncorrectTypeUserFieldAndRemoveIt() {
        // given
        final ObjectNode containerNode = mapper.createObjectNode().put("user", "notObjectType");

        // when
        ortbTypesResolver.normalizeTargeting(containerNode, new ArrayList<>(), "referer");

        // then
        assertThat(containerNode).isEqualTo(mapper.createObjectNode());
    }

    @Test
    public void normalizeBidRequestShouldMergeUserDataToUserExtDataAndRemoveData() {
        // given
        final ObjectNode containerNode = obj("user", obj("data", obj("dataField", "dataValue1"))
                .set("ext", obj("data", obj("extDataField", "extDataValue")
                        .put("dataField", "dataValue2"))));

        // when
        ortbTypesResolver.normalizeBidRequest(containerNode, new ArrayList<>(), "referer");

        // then
        assertThat(containerNode).isEqualTo(obj("user", obj("ext", obj("data", obj("extDataField", "extDataValue")
                .put("dataField", "dataValue1")))));
    }

    @Test
    public void normalizeBidRequestShouldMergeSiteDataToSiteExtDataAndRemoveData() {
        // given
        final ObjectNode containerNode = obj("site", obj("data", obj("dataField", "dataValue1"))
                .set("ext", obj("data", obj("extDataField", "extDataValue")
                        .put("dataField", "dataValue2"))));

        // when
        ortbTypesResolver.normalizeBidRequest(containerNode, new ArrayList<>(), "referer");

        // then
        assertThat(containerNode).isEqualTo(obj("site", obj("ext", obj("data", obj("extDataField", "extDataValue")
                .put("dataField", "dataValue1")))));
    }

    @Test
    public void normalizeBidRequestShouldMergeAppDataToAppExtDataAndRemoveData() {
        // given
        final ObjectNode containerNode = obj("app", obj("data", obj("dataField", "dataValue1"))
                .set("ext", obj("data", obj("extDataField", "extDataValue")
                        .put("dataField", "dataValue2"))));

        // when
        ortbTypesResolver.normalizeBidRequest(containerNode, new ArrayList<>(), "referer");

        // then
        assertThat(containerNode).isEqualTo(obj("app", obj("ext", obj("data", obj("extDataField", "extDataValue")
                .put("dataField", "dataValue1")))));
    }

    @Test
    public void normalizeBidRequestShouldNotChangeUserWhenUserDataNotDefined() {
        // given
        final ObjectNode containerNode = obj("user", obj("ext", obj("data", obj("extDataField", "extDataValue"))));

        // when
        ortbTypesResolver.normalizeBidRequest(containerNode, new ArrayList<>(), "referer");

        // then
        assertThat(containerNode).isEqualTo(obj("user", obj("ext", obj("data", obj("extDataField", "extDataValue")))));
    }

    @Test
    public void normalizeBidRequestShouldNotChangeUserWhenUserDataNotObject() {
        // given
        final ObjectNode containerNode = obj("user", obj("ext", obj("data", obj("extDataField", "extDataValue"))))
                .set("data", mapper.createArrayNode().add(obj("id", "123")));

        // when
        ortbTypesResolver.normalizeBidRequest(containerNode, new ArrayList<>(), "referer");

        // then
        assertThat(containerNode).isEqualTo(
                obj("user", obj("ext", obj("data", obj("extDataField", "extDataValue"))))
                        .set("data", array(obj("id", "123"))));
    }

    @Test
    public void normalizeBidRequestShouldSetDataToUserIfExtDataNotExist() {
        // given
        final ObjectNode containerNode = obj("user", obj("data", obj("dataField", "dataValue"))
                .set("ext", obj("extField", "extValue")));

        // when
        ortbTypesResolver.normalizeBidRequest(containerNode, new ArrayList<>(), "referer");

        // then
        assertThat(containerNode).isEqualTo(obj("user", obj("ext", obj("data", obj("dataField", "dataValue"))
                .put("extField", "extValue"))));
    }

    @Test
    public void normalizeBidRequestShouldSetExtDataToUserIfExtNotExist() {
        // given
        final ObjectNode containerNode = obj("user", obj("data", obj("dataField", "dataValue")));

        // when
        ortbTypesResolver.normalizeBidRequest(containerNode, new ArrayList<>(), "referer");

        // then
        assertThat(containerNode).isEqualTo(obj("user", obj("ext", obj("data", obj("dataField", "dataValue")))));
    }

    @Test
    public void normalizeBidRequestShouldSetExtDataToUserIfExtIncorrectType() {
        // given
        final ObjectNode containerNode = obj("user", obj("data", obj("dataField", "dataValue"))
                .set("ext", mapper.createArrayNode()));
        final List<String> warnings = new ArrayList<>();

        // when
        ortbTypesResolver.normalizeBidRequest(containerNode, warnings, "referer");

        // then
        assertThat(containerNode).isEqualTo(obj("user", obj("ext", obj("data", obj("dataField", "dataValue")))));
        assertThat(warnings).hasSize(1).containsOnly("WARNING: Incorrect type for first party data field"
                + " bidrequest.user.ext, expected is object, but was ARRAY. Replaced with object");
    }

    @Test
    public void normalizeBidRequestShouldResolveEmptyOrtbWithFpdFieldsWithIdForRequestAndExcludedIdForBidderConfig() {
        // given
        final ObjectNode ortbSite = mapper.createObjectNode();
        ortbSite.set("id", array("id1", "id2"));
        ortbSite.set("name", array("name1", "name2"));
        ortbSite.set("domain", array("domain1", "domain2"));
        ortbSite.set("page", array("page1", "page2"));
        ortbSite.set("ref", array("ref1", "ref2"));
        ortbSite.set("search", array("search1", "search2"));
        ortbSite.set("keywords", array("keyword1", "keyword2"));

        final ObjectNode ortbApp = mapper.createObjectNode();
        ortbApp.set("id", array("id1", "id2"));
        ortbApp.set("name", array("name1", "name2"));
        ortbApp.set("bundle", array("bundle1", "bundle2"));
        ortbApp.set("storeurl", array("storeurl1", "storeurl2"));
        ortbApp.set("domain", array("domain1", "domain2"));
        ortbApp.set("keywords", array("keyword1", "keyword2"));

        final ObjectNode ortbUser = mapper.createObjectNode();
        ortbUser.set("gender", array("gender1", "gender2"));
        ortbUser.set("keywords", array("keyword1", "keyword2"));

        final ObjectNode bidderConfigContext = ortbSite.deepCopy();
        final ObjectNode bidderConfigApp = ortbApp.deepCopy();
        final ObjectNode bidderConfigUser = ortbUser.deepCopy();

        final ObjectNode ortbConfig = mapper.createObjectNode();
        ortbConfig.set("site", bidderConfigContext);
        ortbConfig.set("app", bidderConfigApp);
        ortbConfig.set("user", bidderConfigUser);

        final ObjectNode requestNode = mapper.createObjectNode();
        requestNode.set("site", ortbSite);
        requestNode.set("app", ortbApp);
        requestNode.set("user", ortbUser);

        requestNode.set("ext", obj("prebid", obj("bidderconfig", array(obj("config", obj("ortb2", ortbConfig))))));

        // when
        ortbTypesResolver.normalizeBidRequest(requestNode, new ArrayList<>(), "referer");

        // then
        assertThat(requestNode.get("site"))
                .isEqualTo(mapper.createObjectNode()
                        .put("id", "id1")
                        .put("name", "name1")
                        .put("domain", "domain1")
                        .put("page", "page1")
                        .put("ref", "ref1")
                        .put("search", "search1")
                        .put("keywords", "keyword1,keyword2"));

        assertThat(requestNode.get("app"))
                .isEqualTo(mapper.createObjectNode()
                        .put("id", "id1")
                        .put("name", "name1")
                        .put("bundle", "bundle1")
                        .put("storeurl", "storeurl1")
                        .put("domain", "domain1")
                        .put("keywords", "keyword1,keyword2"));

        assertThat(requestNode.get("user"))
                .isEqualTo(mapper.createObjectNode()
                        .put("gender", "gender1")
                        .put("keywords", "keyword1,keyword2"));

        final JsonNode ortb2 = requestNode.path("ext").path("prebid").path("bidderconfig").path(0).path("config")
                .path("ortb2");

        assertThat(ortb2.path("site"))
                .isEqualTo(mapper.createObjectNode()
                        .put("name", "name1")
                        .put("domain", "domain1")
                        .put("page", "page1")
                        .put("ref", "ref1")
                        .put("search", "search1")
                        .put("keywords", "keyword1,keyword2")
                        .set("id", array("id1", "id2")));

        assertThat(ortb2.path("app"))
                .isEqualTo(mapper.createObjectNode()
                        .put("name", "name1")
                        .put("bundle", "bundle1")
                        .put("storeurl", "storeurl1")
                        .put("domain", "domain1")
                        .put("keywords", "keyword1,keyword2")
                        .set("id", array("id1", "id2")));

        assertThat(ortb2.path("user"))
                .isEqualTo(mapper.createObjectNode()
                        .put("gender", "gender1")
                        .put("keywords", "keyword1,keyword2"));
    }

    @Test
    public void normalizeBidRequestShouldBeMergedWithFpdContextToOrtbSite() {
        // given
        final ObjectNode fpdContext = mapper.createObjectNode();
        fpdContext.set("id", array("id1", "id2"));
        fpdContext.set("name", array("name1", "name2"));
        fpdContext.set("domain", array("domain1"));
        fpdContext.set("page", array("page1", "page2"));
        fpdContext.set("data", obj("fpdData", "data_value"));

        final ObjectNode ortbSite = mapper.createObjectNode();
        ortbSite.put("id", "ortb_id");
        // name is absent here
        ortbSite.set("domain", array("ortb_domain1"));
        ortbSite.set("page", array("ortb_page1", "ortb_page2"));
        ortbSite.set("ref", array("ortb_ref1", "ortb_ref2"));
        ortbSite.set("keywords", array("ortb_keyword1", "ortb_keyword2"));
        ortbSite.set("data", obj("ortbData", "ortb_data_value"));

        final ObjectNode configNode = mapper.createObjectNode();
        configNode.set("fpd", obj("context", fpdContext));
        configNode.set("ortb2", obj("site", ortbSite));

        final ObjectNode requestNode = obj("ext", obj("prebid", obj("bidderconfig", array(obj("config", configNode)))));

        final ObjectNode requestedFpdContext = fpdContext.deepCopy();

        // when
        ortbTypesResolver.normalizeBidRequest(requestNode, new ArrayList<>(), "referer");

        // then
        final JsonNode config = requestNode.path("ext").path("prebid").path("bidderconfig").path(0);
        final JsonNode fpd = config.path("config").path("fpd");
        final JsonNode ortb2 = config.path("config").path("ortb2");

        final ObjectNode expectedOrtbExtData = mapper.createObjectNode()
                .put("ortbData", "ortb_data_value")
                .put("fpdData", "data_value");

        final ObjectNode expectedOrtb = mapper.createObjectNode()
                .put("name", "name1")
                .put("domain", "domain1")
                .put("page", "page1")
                .put("ref", "ortb_ref1")
                .put("keywords", "ortb_keyword1,ortb_keyword2");
        expectedOrtb.set("id", array("id1", "id2"));
        expectedOrtb.set("ext", obj("data", expectedOrtbExtData));

        assertThat(ortb2.path("site")).isEqualTo(expectedOrtb);
        assertThat(fpd.path("context")).isEqualTo(requestedFpdContext);
    }

    @Test
    public void normalizeBidRequestShouldBeMergedWithFpdUserToOrtbUser() {
        // given
        final ObjectNode fpdUser = mapper.createObjectNode();
        fpdUser.set("gender", array("gender1", "gender2"));
        fpdUser.set("data", obj("fpdData", "data_value"));

        final ObjectNode ortbUser = mapper.createObjectNode();
        ortbUser.set("gender", array("ortb_gender1", "ortb_gender2"));
        ortbUser.set("keywords", array("ortb_keyword1", "ortb_keyword2"));
        ortbUser.set("data", obj("ortbData", "ortb_data_value"));

        final ObjectNode configNode = mapper.createObjectNode();
        configNode.set("fpd", obj("user", fpdUser));
        configNode.set("ortb2", obj("user", ortbUser));

        final ObjectNode requestNode = obj("ext", obj("prebid", obj("bidderconfig", array(obj("config", configNode)))));

        final ObjectNode requestFpdUser = fpdUser.deepCopy();

        // when
        ortbTypesResolver.normalizeBidRequest(requestNode, new ArrayList<>(), "referer");

        // then
        final JsonNode config = requestNode.path("ext").path("prebid").path("bidderconfig").path(0);
        final JsonNode fpd = config.path("config").path("fpd");
        final JsonNode ortb2 = config.path("config").path("ortb2");

        final ObjectNode expectedOrtbExtData = mapper.createObjectNode()
                .put("ortbData", "ortb_data_value")
                .put("fpdData", "data_value");

        assertThat(ortb2.path("user"))
                .isEqualTo(mapper.createObjectNode()
                        .put("gender", "gender1")
                        .put("keywords", "ortb_keyword1,ortb_keyword2")
                        .set("ext", obj("data", expectedOrtbExtData)));

        assertThat(fpd.path("user")).isEqualTo(requestFpdUser);
    }

    @Test
    public void normalizeBidRequestShouldNotBeMergedWithFpdAppToOrtbApp() {
        // given
        final ObjectNode fpdApp = mapper.createObjectNode();
        fpdApp.set("id", array("id1", "id2"));
        fpdApp.set("name", array("name1", "name2"));
        fpdApp.set("bundle", array("bundle1", "bundle2"));
        fpdApp.set("storeurl", array("storeurl1", "storeurl2"));
        fpdApp.set("domain", array("domain1", "domain2"));
        fpdApp.set("keywords", array("keyword1", "keyword2"));

        final ObjectNode ortbApp = mapper.createObjectNode();
        fpdApp.put("id", "ortb_id");
        // name is absent here
        fpdApp.put("bundle", "ortb_bundle1");
        fpdApp.put("storeurl", "ortb_storeurl1");
        fpdApp.put("keywords", "ortb_keyword1");

        final ObjectNode configNode = mapper.createObjectNode();
        configNode.set("fpd", obj("app", fpdApp));
        configNode.set("ortb2", obj("app", ortbApp));

        final ObjectNode requestNode = obj("ext", obj("prebid", obj("bidderconfig", array(obj("config", configNode)))));

        final ObjectNode requestFpdApp = fpdApp.deepCopy();
        final ObjectNode requestOrtbApp = ortbApp.deepCopy();

        // then
        final JsonNode config = requestNode.path("ext").path("prebid").path("bidderconfig").path(0);
        final JsonNode fpd = config.path("config").path("fpd");
        final JsonNode ortb2 = config.path("config").path("ortb2");

        assertThat(ortb2.path("app")).isEqualTo(requestOrtbApp);
        assertThat(fpd.path("app")).isEqualTo(requestFpdApp);
    }

    private static ArrayNode array(String... fields) {
        final ArrayNode arrayNode = mapper.createArrayNode();
        Arrays.stream(fields).forEach(arrayNode::add);
        return arrayNode;
    }

    private static ArrayNode array(ObjectNode... nodes) {
        final ArrayNode arrayNode = mapper.createArrayNode();
        Arrays.stream(nodes).forEach(arrayNode::add);
        return arrayNode;
    }

    private static ObjectNode obj(String fieldName, JsonNode value) {
        return mapper.createObjectNode().set(fieldName, value);
    }

    private static ObjectNode obj(String fieldName, String value) {
        return mapper.createObjectNode().put(fieldName, value);
    }
}
