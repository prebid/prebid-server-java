package org.prebid.server.auction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;
import org.prebid.server.VertxTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class OrtbTypesResolverTest extends VertxTest {

    private final OrtbTypesResolver ortbTypesResolver = new OrtbTypesResolver();

    @Test
    public void normalizeStandardFpdFieldsShouldNotChangeNodeIfItsTypeIsNotObject() {
        // given
        final JsonNode inputParam = mapper.createArrayNode();

        // when
        ortbTypesResolver.normalizeStandardFpdFields(inputParam, new ArrayList<>(), "rootNode");

        // then
        assertThat(inputParam).isEqualTo(mapper.createArrayNode());
    }

    @Test
    public void normalizeStandardFpdFieldsShouldConvertArrayToFirstElementFieldForUserAndWriteMessage() {
        // given
        final JsonNode inputParam = mapper.createObjectNode().set("user",
                mapper.createObjectNode().set("gender", mapper.createArrayNode().add("male").add("female")));
        final List<String> errors = new ArrayList<>();

        // when
        ortbTypesResolver.normalizeStandardFpdFields(inputParam, errors, "rootNode");

        // then
        assertThat(inputParam).isEqualTo(mapper.createObjectNode().set("user",
                mapper.createObjectNode().put("gender", "male")));
        assertThat(errors).containsOnly("Incorrect type for first party data field rootNode.user.gender, expected is"
                + " string, but was an array of strings. Converted to string by taking first element of array.");
    }

    @Test
    public void normalizeStandardFpdFieldsShouldConvertArrayToCommaSeparatedStringFieldForUserAndWriteMessage() {
        // given
        final JsonNode inputParam = mapper.createObjectNode().set("user",
                mapper.createObjectNode().set("keywords", mapper.createArrayNode().add("keyword1").add("keyword2")));
        final List<String> errors = new ArrayList<>();

        // when
        ortbTypesResolver.normalizeStandardFpdFields(inputParam, errors, "rootNode");

        // then
        assertThat(inputParam).isEqualTo(mapper.createObjectNode().set("user",
                mapper.createObjectNode().put("keywords", "keyword1,keyword2")));
        assertThat(errors).containsOnly("Incorrect type for first party data field rootNode.user.keywords, expected is"
                + " string, but was an array of strings. Converted to string by separating values with comma.");
    }

    @Test
    public void normalizeStandardFpdFieldsShouldSkipUserNormalizationIfTypeIsNotObject() {
        // given
        final JsonNode inputParam = mapper.createObjectNode().set("user", mapper.createArrayNode());
        final List<String> errors = new ArrayList<>();

        // when
        ortbTypesResolver.normalizeStandardFpdFields(inputParam, errors, "rootNode");

        // then
        assertThat(inputParam).isEqualTo(mapper.createObjectNode().set("user", mapper.createArrayNode()));
    }

    @Test
    public void normalizeStandardFpdFieldsShouldSkipUserNormalizationIfNull() {
        final JsonNode inputParam = mapper.createObjectNode().set("user", null);
        final List<String> errors = new ArrayList<>();

        // when
        ortbTypesResolver.normalizeStandardFpdFields(inputParam, errors, "rootNode");

        // then
        assertThat(inputParam).isEqualTo(mapper.createObjectNode().set("user", null));
    }

    @Test
    public void normalizeStandardFpdFieldsToCommaSeparatedTextNodeShouldWriteMessageIfNormalizationIsNotPossible() {
        // given
        final JsonNode inputParam = mapper.createObjectNode().set("user",
                mapper.createObjectNode().set("keywords", mapper.createArrayNode().add("keyword1").add(2)));
        final List<String> errors = new ArrayList<>();

        // when
        ortbTypesResolver.normalizeStandardFpdFields(inputParam, errors, "rootNode");

        // then
        assertThat(inputParam).isEqualTo(mapper.createObjectNode().set("user",
                mapper.createObjectNode().set("keywords", mapper.createArrayNode().add("keyword1").add(2))));
        assertThat(errors).containsOnly("Incorrect type for first party data field rootNode.user.keywords,"
                + " expected strings, but was `ARRAY of different types`. Failed to convert to correct type.");
    }

    @Test
    public void normalizeStandardFpdFieldsToFirstElementTextNodeShouldWriteMessageIfNormalizationIsNotPossible() {
        // given
        final JsonNode inputParam = mapper.createObjectNode()
                .set("user", mapper.createObjectNode().set("gender", new IntNode(1)));
        final List<String> errors = new ArrayList<>();

        // when
        ortbTypesResolver.normalizeStandardFpdFields(inputParam, errors, "rootNode");

        // then
        assertThat(inputParam).isEqualTo(mapper.createObjectNode()
                .set("user", mapper.createObjectNode().set("gender", new IntNode(1))));
        assertThat(errors).containsOnly("Incorrect type for first party data field rootNode.user.gender,"
                + " expected strings, but was `NUMBER`. Failed to convert to correct type.");
    }

    @Test
    public void normalizeStandardFpdFieldsShouldNormalizeFieldsForUser() {
        // given
        final ObjectNode user = mapper.createObjectNode().set("gender", mapper.createArrayNode().add("gender1")
                .add("gender2"));
        user.set("keywords", mapper.createArrayNode().add("keyword1").add("keyword2"));
        final ObjectNode containerNode = mapper.createObjectNode().set("user", user);

        // when
        ortbTypesResolver.normalizeStandardFpdFields(containerNode, new ArrayList<>(), "rootNode");

        // then
        assertThat(containerNode).isEqualTo(mapper.createObjectNode().set("user", mapper.createObjectNode()
                .put("gender", "gender1")
                .put("keywords", "keyword1,keyword2")));
    }

    @Test
    public void normalizeStandardFpdFieldsShouldNormalizeFieldsForAppExceptId() {
        // given
        final ObjectNode app = mapper.createObjectNode();
        app.set("id", mapper.createArrayNode().add("id1").add("id2"));
        app.set("name", mapper.createArrayNode().add("name1").add("name2"));
        app.set("bundle", mapper.createArrayNode().add("bundle1").add("bundle2"));
        app.set("storeurl", mapper.createArrayNode().add("storeurl1").add("storeurl2"));
        app.set("domain", mapper.createArrayNode().add("domain1").add("domain2"));
        app.set("keywords", mapper.createArrayNode().add("keyword1").add("keyword2"));
        final ObjectNode containerNode = mapper.createObjectNode().set("app", app);

        // when
        ortbTypesResolver.normalizeStandardFpdFields(containerNode, new ArrayList<>(), "rootNode");

        // then
        assertThat(containerNode).isEqualTo(mapper.createObjectNode().set("app", mapper.createObjectNode()
                .put("name", "name1")
                .put("bundle", "bundle1")
                .put("storeurl", "storeurl1")
                .put("domain", "domain1")
                .put("keywords", "keyword1,keyword2")
                .set("id", mapper.createArrayNode().add("id1").add("id2"))));
    }

    @Test
    public void normalizeStandardFpdFieldsShouldNormalizeFieldsForSiteExceptId() {
        // given
        final ObjectNode site = mapper.createObjectNode();
        site.set("id", mapper.createArrayNode().add("id1").add("id2"));
        site.set("name", mapper.createArrayNode().add("name1").add("name2"));
        site.set("domain", mapper.createArrayNode().add("domain1").add("domain2"));
        site.set("page", mapper.createArrayNode().add("page1").add("page2"));
        site.set("ref", mapper.createArrayNode().add("ref1").add("ref2"));
        site.set("search", mapper.createArrayNode().add("search1").add("search2"));
        site.set("keywords", mapper.createArrayNode().add("keyword1").add("keyword2"));
        final ObjectNode containerNode = mapper.createObjectNode().set("site", site);

        // when
        ortbTypesResolver.normalizeStandardFpdFields(containerNode, new ArrayList<>(), "rootNode");

        // then
        assertThat(containerNode).isEqualTo(mapper.createObjectNode().set("site", mapper.createObjectNode()
                .put("name", "name1")
                .put("page", "page1")
                .put("ref", "ref1")
                .put("domain", "domain1")
                .put("search", "search1")
                .put("keywords", "keyword1,keyword2")
                .set("id", mapper.createArrayNode().add("id1").add("id2")))
        );
    }

    @Test
    public void normalizeBidRequestShouldResolveORTBFieldsWithIdForRequestAndExcludedIdForBidderConfig() {
        // given
        final ObjectNode requestNode = mapper.createObjectNode();

        final ObjectNode requestSite = mapper.createObjectNode();
        requestSite.set("id", mapper.createArrayNode().add("id1").add("id2"));
        requestSite.set("name", mapper.createArrayNode().add("name1").add("name2"));
        requestSite.set("domain", mapper.createArrayNode().add("domain1").add("domain2"));
        requestSite.set("page", mapper.createArrayNode().add("page1").add("page2"));
        requestSite.set("ref", mapper.createArrayNode().add("ref1").add("ref2"));
        requestSite.set("search", mapper.createArrayNode().add("search1").add("search2"));
        requestSite.set("keywords", mapper.createArrayNode().add("keyword1").add("keyword2"));

        final ObjectNode requestApp = mapper.createObjectNode();
        requestApp.set("id", mapper.createArrayNode().add("id1").add("id2"));
        requestApp.set("name", mapper.createArrayNode().add("name1").add("name2"));
        requestApp.set("bundle", mapper.createArrayNode().add("bundle1").add("bundle2"));
        requestApp.set("storeurl", mapper.createArrayNode().add("storeurl1").add("storeurl2"));
        requestApp.set("domain", mapper.createArrayNode().add("domain1").add("domain2"));
        requestApp.set("keywords", mapper.createArrayNode().add("keyword1").add("keyword2"));

        final ObjectNode requestUser = mapper.createObjectNode();
        requestUser.set("gender", mapper.createArrayNode().add("gender1").add("gender2"));
        requestUser.set("keywords", mapper.createArrayNode().add("keyword1").add("keyword2"));

        final ObjectNode bidderConfigSite1 = requestSite.deepCopy();
        final ObjectNode bidderConfigApp1 = requestApp.deepCopy();
        final ObjectNode bidderConfigUser1 = requestUser.deepCopy();

        final ObjectNode bidderConfig1 = mapper.createObjectNode();
        bidderConfig1.set("site", bidderConfigSite1);
        bidderConfig1.set("app", bidderConfigApp1);
        bidderConfig1.set("user", bidderConfigUser1);

        requestNode.set("site", requestSite);
        requestNode.set("user", requestUser);
        requestNode.set("app", requestApp);

        requestNode.set("ext", mapper.createObjectNode().set("prebid", mapper.createObjectNode().set("bidderconfig",
                mapper.createArrayNode()
                .add(mapper.createObjectNode().set("config", mapper.createObjectNode().set("fpd", bidderConfig1))))));
        // when
        ortbTypesResolver.normalizeBidRequest(requestNode, new ArrayList<>());

        // then
        assertThat(requestNode.get("site"))
                .isEqualTo(mapper.createObjectNode().put("id", "id1").put("name", "name1").put("domain", "domain1")
                        .put("page", "page1").put("ref", "ref1").put("search", "search1")
                        .put("keywords", "keyword1,keyword2"));

        assertThat(requestNode.get("app"))
                .isEqualTo(mapper.createObjectNode().put("id", "id1").put("name", "name1").put("bundle", "bundle1")
                        .put("storeurl", "storeurl1").put("domain", "domain1")
                        .put("keywords", "keyword1,keyword2"));

        assertThat(requestNode.get("user"))
                .isEqualTo(mapper.createObjectNode().put("gender", "gender1").put("keywords", "keyword1,keyword2"));

        assertThat(requestNode.path("ext").path("prebid").path("bidderconfig").path(0).path("config").path("fpd")
                .path("site"))
                .isEqualTo(mapper.createObjectNode().put("name", "name1").put("domain", "domain1").put("page", "page1")
                        .put("ref", "ref1").put("search", "search1").put("keywords", "keyword1,keyword2")
                        .set("id", mapper.createArrayNode().add("id1").add("id2")));

        assertThat(requestNode.path("ext").path("prebid").path("bidderconfig").path(0).path("config").path("fpd")
                .path("app"))
                .isEqualTo(mapper.createObjectNode().put("name", "name1").put("bundle", "bundle1")
                        .put("storeurl", "storeurl1").put("domain", "domain1")
                        .put("keywords", "keyword1,keyword2")
                        .set("id", mapper.createArrayNode().add("id1").add("id2")));

        assertThat(requestNode.path("ext").path("prebid").path("bidderconfig").path(0).path("config").path("fpd")
                .path("user"))
                .isEqualTo(mapper.createObjectNode().put("gender", "gender1").put("keywords", "keyword1,keyword2"));
    }
}
