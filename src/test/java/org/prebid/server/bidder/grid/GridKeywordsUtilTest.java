package org.prebid.server.bidder.grid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.grid.model.KeywordSegment;
import org.prebid.server.bidder.grid.model.Keywords;
import org.prebid.server.bidder.grid.model.KeywordsPublisherItem;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class GridKeywordsUtilTest extends VertxTest {

    @Test
    public void modifyWithKeywordsShouldCorrectlyAddKeywordsSections() {
        // given
        final Keywords keywords = Keywords.of(
                givenKeywordsSectionFromOpenRtb("user"),
                givenKeywordsSectionFromOpenRtb("site"));

        final Map<String, JsonNode> initialProps = new HashMap<>();
        initialProps.put("test", TextNode.valueOf("value"));

        // when
        final Map<String, JsonNode> result =
                GridKeywordsUtil.modifyWithKeywords(initialProps, keywords, jacksonMapper);

        // then
        assertThat(result).containsEntry("test", TextNode.valueOf("value"));
        assertThat(result).containsEntry("keywords", mapper.valueToTree(keywords));
    }

    @Test
    public void modifyWithKeywordsShouldDeleteKeywordsKeyIfValueEmptyOrNull() {
        // given
        final Keywords keywords = Keywords.of(null, mapper.createObjectNode());

        final Map<String, JsonNode> initialProps = new HashMap<>();
        initialProps.put("test", TextNode.valueOf("value"));

        // when
        final Map<String, JsonNode> result =
                GridKeywordsUtil.modifyWithKeywords(initialProps, keywords, jacksonMapper);

        // then
        assertThat(result).containsEntry("test", TextNode.valueOf("value"));
        assertThat(result).doesNotContainKey("keywords");
    }

    @Test
    public void resolveKeywordsSectionFromOpenRtbShouldCorrectlyResolveKeywords() {
        // given and when
        final ObjectNode result = GridKeywordsUtil.resolveKeywordsSectionFromOpenRtb(
                "keyword1,keyword2", jacksonMapper);

        // then
        assertThat(result).isEqualTo(givenKeywordsSectionFromOpenRtb("keyword1", "keyword2"));
    }

    @Test
    public void resolveKeywordsSectionFromOpenRtbShouldReturnEmptyNodeIfKeywordsAreEmpty() {
        // given and when
        final ObjectNode result = GridKeywordsUtil.resolveKeywordsSectionFromOpenRtb(
                "", jacksonMapper);

        // then
        assertThat(result).isEqualTo(mapper.createObjectNode());
    }

    @Test
    public void resolveKeywordsFromOpenRtbShouldCorrectlyResolveKeywordsFromSiteAndUserSections() {
        // given and when
        final Keywords result = GridKeywordsUtil.resolveKeywordsFromOpenRtb(
                "userKeyword", "siteKeyword", jacksonMapper);

        // then
        assertThat(result).isEqualTo(
                Keywords.of(
                        givenKeywordsSectionFromOpenRtb("userKeyword"),
                        givenKeywordsSectionFromOpenRtb("siteKeyword")));
    }

    @Test
    public void resolvePublisherSegmentShouldReturnCorrectKeywordsSegment() {
        // given
        final JsonNode segmentNode = givenPublisherSegmentNode("name", "value");

        // when
        final KeywordSegment result = GridKeywordsUtil.resolvePublisherSegment(segmentNode);

        // then
        assertThat(result).isEqualTo(KeywordSegment.of("name", "value"));
    }

    @Test
    public void resolvePublisherSegmentShouldReturnNullIfNameIsAbsent() {
        // given
        final JsonNode segmentNode = givenPublisherSegmentNode(null, "value");

        // when and then
        assertThat(GridKeywordsUtil.resolvePublisherSegment(segmentNode)).isEqualTo(null);
    }

    @Test
    public void resolvePublisherSegmentShouldReturnNullIfValueIsAbsent() {
        // given
        final JsonNode segmentNode = givenPublisherSegmentNode("name", null);

        // when and then
        assertThat(GridKeywordsUtil.resolvePublisherSegment(segmentNode)).isEqualTo(null);
    }

    @Test
    public void resolvePublisherSegmentsShouldCorrectlyResolveSegments() {
        // given
        final JsonNode segmentsNode = givenPublisherSegmentsNode(
                givenPublisherSegmentNode("name1", "value1"),
                givenPublisherSegmentNode("name2", "value2"));

        // when
        final List<KeywordSegment> result = GridKeywordsUtil.resolvePublisherSegments(segmentsNode);

        // then
        assertThat(result).containsExactlyInAnyOrder(
                KeywordSegment.of("name1", "value1"),
                KeywordSegment.of("name2", "value2"));
    }

    @Test
    public void resolvePublisherSegmentsShouldSkipNotArrayNodes() {
        // given
        final JsonNode segmentsNode = givenPublisherSegmentsNode(TextNode.valueOf("brokenNode"), null);

        // when
        final List<KeywordSegment> result = GridKeywordsUtil.resolvePublisherSegments(segmentsNode);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void resolvePublisherSegmentsShouldReturnEmptyListIfSegmentsNodeIsNotArray() {
        // given and when
        final List<KeywordSegment> result = GridKeywordsUtil.resolvePublisherSegments(TextNode.valueOf(""));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void resolvePublisherKeywordsShouldCorrectlyResolveKeywords() {
        // given
        final JsonNode publisherSegmentsNode = givenPublisherSegmentsNode(
                givenPublisherSegmentNode("segmentName", "segmentValue"));
        final JsonNode publisherSectionItemNode = givenPublisherSectionItemNode(
                "sectionName", publisherSegmentsNode);
        final JsonNode publisherNode = mapper.createArrayNode().add(publisherSectionItemNode);

        // when
        final List<KeywordsPublisherItem> result = GridKeywordsUtil.resolvePublisherKeywords(
                publisherNode, jacksonMapper);

        // then
        assertThat(result).containsExactly(
                KeywordsPublisherItem.of(
                        "sectionName",
                        singletonList(KeywordSegment.of("segmentName", "segmentValue"))));
    }

    @Test
    public void resolveAlternativePublisherSegmentsShouldCorrectlyResolveAlternativeSegments() {
        // given
        final JsonNode alternativePublisherSegmentNode = givenAlternativePublisherSegments("foo", "bar");
        final ObjectNode publisherSectionItemNode = mapper.createObjectNode()
                .set("alternativeSectionName", alternativePublisherSegmentNode);

        // when
        final List<KeywordSegment> result = GridKeywordsUtil.resolveAlternativePublisherSegments(
                publisherSectionItemNode, jacksonMapper);

        // then
        assertThat(result).containsExactlyInAnyOrder(
                KeywordSegment.of("alternativeSectionName", "foo"),
                KeywordSegment.of("alternativeSectionName", "bar"));
    }

    @Test
    public void resolveAlternativePublisherSegmentsShouldSortAlternativeSegmentsByKey() {
        // given
        final ObjectNode publisherSectionItemNode = mapper.createObjectNode();

        final JsonNode firstAlternativePublisherSegmentNode = givenAlternativePublisherSegments("foo");
        final JsonNode secondAlternativePublisherSegmentNode = givenAlternativePublisherSegments("val");
        final JsonNode thirdAlternativePublisherSegmentNode = givenAlternativePublisherSegments("bar");

        publisherSectionItemNode.set("b", firstAlternativePublisherSegmentNode);
        publisherSectionItemNode.set("a", secondAlternativePublisherSegmentNode);
        publisherSectionItemNode.set("c", thirdAlternativePublisherSegmentNode);

        // when
        final List<KeywordSegment> result = GridKeywordsUtil.resolveAlternativePublisherSegments(
                publisherSectionItemNode, jacksonMapper);

        // then
        assertThat(result).containsExactly(
                KeywordSegment.of("a", "val"),
                KeywordSegment.of("b", "foo"),
                KeywordSegment.of("c", "bar"));
    }

    @Test
    public void resolvePublisherKeywordsShouldSkipInvalidSectionItems() {
        // given
        final JsonNode publisherNode = mapper.createArrayNode()
                .add(mapper.createObjectNode())
                .add(TextNode.valueOf("invalidItem"));

        // when
        final List<KeywordsPublisherItem> result = GridKeywordsUtil.resolvePublisherKeywords(
                publisherNode, jacksonMapper);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void resolvePublisherKeywordsShouldCorrectlyResolveMainAndAlternativeSegments() {
        // given
        final JsonNode alternativePublisherSegmentNode = givenAlternativePublisherSegments("foo", "bar");
        final JsonNode publisherSegmentsNode = givenPublisherSegmentsNode(
                givenPublisherSegmentNode("segmentName", "segmentValue"));

        final ObjectNode publisherSectionItemNode = givenPublisherSectionItemNode(
                "sectionName", publisherSegmentsNode);
        publisherSectionItemNode.set("alternativeSectionName", alternativePublisherSegmentNode);

        final ArrayNode publisherSectionItemsNode = mapper.createArrayNode().add(publisherSectionItemNode);

        // when
        final List<KeywordsPublisherItem> result = GridKeywordsUtil.resolvePublisherKeywords(
                publisherSectionItemsNode, jacksonMapper);

        // then
        assertThat(result)
                .extracting(KeywordsPublisherItem::getName)
                .containsExactly("sectionName");

        assertThat(result)
                .flatExtracting(KeywordsPublisherItem::getSegments)
                .containsExactlyInAnyOrder(
                        KeywordSegment.of("segmentName", "segmentValue"),
                        KeywordSegment.of("alternativeSectionName", "foo"),
                        KeywordSegment.of("alternativeSectionName", "bar"));
    }

    @Test
    public void resolveKeywordsShouldReturnEmptyKeywordsIfKeywordsIsNull() {
        // given and when
        final Keywords result = GridKeywordsUtil.resolveKeywords(null, jacksonMapper);

        // then
        assertThat(result).isEqualTo(Keywords.empty());
    }

    @Test
    public void resolveKeywordsShouldCorrectlyResolveUserAndSiteSections() throws IOException {
        // given
        final ObjectNode userSectionNode = (ObjectNode) jsonNodeFrom("bidder/grid/util/site-section.json");
        final ObjectNode siteSectionNode = (ObjectNode) jsonNodeFrom("bidder/grid/util/user-section.json");

        final Keywords keywords = Keywords.of(userSectionNode, siteSectionNode);

        // when
        final Keywords result = GridKeywordsUtil.resolveKeywords(keywords, jacksonMapper);

        // then
        assertThat(result).isEqualTo(Keywords.of(userSectionNode, siteSectionNode));
    }

    @Test
    public void mergeShouldCorrectlyMergeKeywordsArraysNodes() throws IOException {
        // given
        final JsonNode firstKeywordsNode = jsonNodeFrom("bidder/grid/util/keywords-arrays-nodes-1.json");
        final JsonNode secondKeywordsNode = jsonNodeFrom("bidder/grid/util/keywords-arrays-nodes-2.json");

        final Keywords firstKeywords = mapper.convertValue(firstKeywordsNode, Keywords.class);
        final Keywords secondKeywords = mapper.convertValue(secondKeywordsNode, Keywords.class);

        // when
        final Keywords result = GridKeywordsUtil.merge(jacksonMapper, firstKeywords, secondKeywords);

        // then
        final JsonNode expectedResultNode = jsonNodeFrom(
                "bidder/grid/util/keywords-arrays-nodes-merge-result.json.json");

        assertThat(result)
                .isEqualTo(mapper.convertValue(expectedResultNode, Keywords.class));
    }

    @Test
    public void mergeShouldCorrectlyMergeSectionsPublishersArraysNodes() throws IOException {
        // given
        final JsonNode firstKeywordsNode = jsonNodeFrom(
                "bidder/grid/util/publishers-arrays-nodes-keywords-1.json");
        final Keywords firstKeywords = mapper.convertValue(firstKeywordsNode, Keywords.class);

        final JsonNode secondKeywordsNode = jsonNodeFrom(
                "bidder/grid/util/publishers-arrays-nodes-keywords-2.json");
        final Keywords secondKeywords = mapper.convertValue(secondKeywordsNode, Keywords.class);

        // when
        final Keywords result = GridKeywordsUtil.merge(jacksonMapper, firstKeywords, secondKeywords);

        // then
        final JsonNode expectedResultNode = jsonNodeFrom(
                "bidder/grid/util/publishers-arrays-nodes-keywords-merge-result.json");

        assertThat(result)
                .isEqualTo(mapper.convertValue(expectedResultNode, Keywords.class));
    }

    private static ObjectNode givenKeywordsSectionFromOpenRtb(String... keywords) {
        return mapper.createObjectNode().set("ortb2",
                mapper.valueToTree(givenKeywordsPublisherItemsFromOpenRtb(keywords)));
    }

    private static List<KeywordsPublisherItem> givenKeywordsPublisherItemsFromOpenRtb(String... keywords) {
        return singletonList(
                KeywordsPublisherItem.of(
                        "keywords",
                        Arrays.stream(keywords)
                                .map(keyword -> KeywordSegment.of("keywords", keyword))
                                .collect(Collectors.toList())));
    }

    private static ObjectNode givenPublisherSegmentNode(String name, String value) {
        final ObjectNode segmentNode = mapper.createObjectNode();
        segmentNode.set("name", TextNode.valueOf(name));
        segmentNode.set("value", TextNode.valueOf(value));
        return segmentNode;
    }

    private static ArrayNode givenPublisherSegmentsNode(JsonNode... segmentNodes) {
        final ArrayNode publisherSegmentsNode = mapper.createArrayNode();
        Arrays.stream(segmentNodes).forEach(publisherSegmentsNode::add);
        return publisherSegmentsNode;
    }

    private static ObjectNode givenPublisherSectionItemNode(String name, JsonNode segmentsNode) {
        final ObjectNode publisherSectionItemNode = mapper.createObjectNode();
        publisherSectionItemNode.set("name", TextNode.valueOf(name));
        publisherSectionItemNode.set("segments", segmentsNode);
        return publisherSectionItemNode;
    }

    private static ArrayNode givenAlternativePublisherSegments(String... values) {
        return givenPublisherSegmentsNode(Arrays.stream(values).map(TextNode::valueOf).toArray(TextNode[]::new));
    }

    private static JsonNode jsonNodeFrom(String path) throws IOException {
        return mapper.readTree(VertxTest.class.getResourceAsStream(path));
    }
}
