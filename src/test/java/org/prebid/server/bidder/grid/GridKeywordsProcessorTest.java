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

public class GridKeywordsProcessorTest extends VertxTest {

    private final GridKeywordsProcessor gridKeywordsProcessor = new GridKeywordsProcessor(jacksonMapper);

    @Test
    public void modifyWithKeywordsShouldCorrectlyAddKeywordsSections() {
        // given
        final Keywords keywords = Keywords.of(
                givenKeywordsSectionFromOpenRtb("user"),
                givenKeywordsSectionFromOpenRtb("site"));

        final Map<String, JsonNode> initialProps = new HashMap<>();
        initialProps.put("test", TextNode.valueOf("value"));

        // when
        final Map<String, JsonNode> result = gridKeywordsProcessor.modifyWithKeywords(initialProps, keywords);

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
                gridKeywordsProcessor.modifyWithKeywords(initialProps, keywords);

        // then
        assertThat(result).containsEntry("test", TextNode.valueOf("value"));
        assertThat(result).doesNotContainKey("keywords");
    }

    @Test
    public void resolveKeywordsSectionFromOpenRtbShouldCorrectlyResolveKeywords() {
        // given and when
        final ObjectNode result = gridKeywordsProcessor.resolveKeywordsSectionFromOpenRtb(
                "keyword1,keyword2");

        // then
        assertThat(result).isEqualTo(givenKeywordsSectionFromOpenRtb("keyword1", "keyword2"));
    }

    @Test
    public void resolveKeywordsSectionFromOpenRtbShouldReturnEmptyNodeIfKeywordsAreEmpty() {
        // given and when
        final ObjectNode result = gridKeywordsProcessor.resolveKeywordsSectionFromOpenRtb(
                "");

        // then
        assertThat(result).isEqualTo(mapper.createObjectNode());
    }

    @Test
    public void resolveKeywordsFromOpenRtbShouldCorrectlyResolveKeywordsFromSiteAndUserSections() {
        // given and when
        final Keywords result = gridKeywordsProcessor.resolveKeywordsFromOpenRtb(
                "userKeyword", "siteKeyword");

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
        final KeywordSegment result = GridKeywordsProcessor.resolvePublisherSegment(segmentNode);

        // then
        assertThat(result).isEqualTo(KeywordSegment.of("name", "value"));
    }

    @Test
    public void resolvePublisherSegmentShouldReturnNullIfNameIsAbsent() {
        // given
        final JsonNode segmentNode = givenPublisherSegmentNode(null, "value");

        // when and then
        assertThat(GridKeywordsProcessor.resolvePublisherSegment(segmentNode)).isEqualTo(null);
    }

    @Test
    public void resolvePublisherSegmentShouldReturnNullIfValueIsAbsent() {
        // given
        final JsonNode segmentNode = givenPublisherSegmentNode("name", null);

        // when and then
        assertThat(GridKeywordsProcessor.resolvePublisherSegment(segmentNode)).isEqualTo(null);
    }

    @Test
    public void resolvePublisherSegmentsShouldCorrectlyResolveSegments() {
        // given
        final JsonNode segmentsNode = givenPublisherSegmentsNode(
                givenPublisherSegmentNode("name1", "value1"),
                givenPublisherSegmentNode("name2", "value2"));

        // when
        final List<KeywordSegment> result = GridKeywordsProcessor.resolvePublisherSegments(segmentsNode);

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
        final List<KeywordSegment> result = GridKeywordsProcessor.resolvePublisherSegments(segmentsNode);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void resolvePublisherSegmentsShouldReturnEmptyListIfSegmentsNodeIsNotArray() {
        // given and when
        final List<KeywordSegment> result = GridKeywordsProcessor.resolvePublisherSegments(TextNode.valueOf(""));

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
        final List<KeywordsPublisherItem> result = gridKeywordsProcessor.resolvePublisherKeywords(
                publisherNode);

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
        final List<KeywordSegment> result = gridKeywordsProcessor.resolveAlternativePublisherSegments(
                publisherSectionItemNode);

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
        final List<KeywordSegment> result = gridKeywordsProcessor.resolveAlternativePublisherSegments(
                publisherSectionItemNode);

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
        final List<KeywordsPublisherItem> result = gridKeywordsProcessor.resolvePublisherKeywords(
                publisherNode);

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
        final List<KeywordsPublisherItem> result = gridKeywordsProcessor.resolvePublisherKeywords(
                publisherSectionItemsNode);

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
        final Keywords result = gridKeywordsProcessor.resolveKeywords(null);

        // then
        assertThat(result).isEqualTo(Keywords.empty());
    }

    @Test
    public void resolveKeywordsShouldCorrectlyResolveUserAndSiteSections() throws IOException {
        // given
        final ObjectNode userSectionNode = objectNodeFrom("util/site-section.json");
        final ObjectNode siteSectionNode = objectNodeFrom("util/user-section.json");

        final Keywords keywords = Keywords.of(userSectionNode, siteSectionNode);

        // when
        final Keywords result = gridKeywordsProcessor.resolveKeywords(keywords);

        // then
        assertThat(result).isEqualTo(Keywords.of(userSectionNode, siteSectionNode));
    }

    @Test
    public void mergeShouldCorrectlyMergeKeywordsArraysNodes() throws IOException {
        // given
        final Keywords firstKeywords = mapper.convertValue(
                objectNodeFrom("util/keywords-array-nodes-1.json"), Keywords.class);
        final Keywords secondKeywords = mapper.convertValue(
                objectNodeFrom("util/keywords-array-nodes-2.json"), Keywords.class);

        // when
        final Keywords result = gridKeywordsProcessor.merge(firstKeywords, secondKeywords);

        // then
        assertThat(result).isEqualTo(
                mapper.convertValue(objectNodeFrom("util/keywords-array-nodes-merge-result.json"), Keywords.class));
    }

    @Test
    public void mergeShouldCorrectlyMergeSectionsPublishersArraysNodes() throws IOException {
        // given
        final Keywords firstKeywords = mapper.convertValue(
                objectNodeFrom("util/publisher-array-nodes-1.json"), Keywords.class);

        final Keywords secondKeywords = mapper.convertValue(
                objectNodeFrom("util/publisher-array-nodes-2.json"), Keywords.class);

        // when
        final Keywords result = gridKeywordsProcessor.merge(firstKeywords, secondKeywords);

        // then
        assertThat(result).isEqualTo(
                mapper.convertValue(objectNodeFrom("util/publisher-array-nodes-merge-result.json"), Keywords.class));
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

    private static ObjectNode objectNodeFrom(String path) throws IOException {
        return (ObjectNode) mapper.readTree(GridKeywordsProcessor.class.getResourceAsStream(path));
    }
}
