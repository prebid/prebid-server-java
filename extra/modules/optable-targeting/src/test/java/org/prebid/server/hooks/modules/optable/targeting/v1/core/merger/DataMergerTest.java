package org.prebid.server.hooks.modules.optable.targeting.v1.core.merger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Data;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Segment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;

public class DataMergerTest extends BaseMergerTest {

    private DataMerger target;

    @BeforeEach
    public void setUp() {
        target = new DataMerger();
    }

    @Test
    public void shouldMergeDifferentData() {
        // given
        final List<com.iab.openrtb.request.Data> destination = givenORTBData("dataId1", "segmentId1",
                "field1", "value1");
        final List<Data> source = givenOptableData("dataId2", "segmentId2", "field2", "value2");

        // when
        final List<com.iab.openrtb.request.Data> result = target.merge(destination, source);

        // then
        assertThat(result).isNotNull()
                .hasSize(2);

        assertThat(result.getFirst())
                .returns("dataId2", from(com.iab.openrtb.request.Data::getId))
                .returns("segmentId2", it -> it.getSegment().getFirst().getId())
                .returns("value2", it -> it.getSegment().getFirst().getExt().get("field2").asText());

        assertThat(result.get(1))
                .returns("dataId1", from(com.iab.openrtb.request.Data::getId))
                .returns("segmentId1", it -> it.getSegment().getFirst().getId())
                .returns("value1", it -> it.getSegment().getFirst().getExt().get("field1").asText());
    }

    @Test
    public void shouldMergeSegmentsWithinTheSameData() {
        // given
        final List<com.iab.openrtb.request.Data> destination = givenORTBData("dataId1", "segmentId1",
                "field1", "value1");
        final List<Data> source = givenOptableData("dataId1", "segmentId2", "field2", "value2");

        // when
        final List<com.iab.openrtb.request.Data> result = target.merge(destination, source);

        // then
        assertThat(result).isNotNull()
                .hasSize(1);

        assertThat(result.getFirst())
                .returns("dataId1", from(com.iab.openrtb.request.Data::getId))
                .returns("segmentId1", it -> it.getSegment().getFirst().getId())
                .returns("value1", it -> it.getSegment().getFirst().getExt().get("field1").asText())
                .returns("segmentId2", it -> it.getSegment().get(1).getId())
                .returns("value2", it -> it.getSegment().get(1).getExt().get("field2").asText());
    }

    @Test
    public void shouldMergeExtWithinTheSameSegment() {
        // given
        final List<com.iab.openrtb.request.Data> destination = givenORTBData("dataId1", "segmentId1",
                "field1", "value1");
        final List<Data> source = givenOptableData("dataId1", "segmentId1", "field2", "value2");

        // when
        final List<com.iab.openrtb.request.Data> result = target.merge(destination, source);

        // then
        assertThat(result).isNotNull()
                .hasSize(1);

        assertThat(result.getFirst())
                .returns("dataId1", from(com.iab.openrtb.request.Data::getId))
                .returns("segmentId1", it -> it.getSegment().getFirst().getId())
                .returns("value1", it -> it.getSegment().getFirst().getExt().get("field1").asText())
                .returns("value2", it -> it.getSegment().getFirst().getExt().get("field2").asText());
    }

    @Test
    public void shouldUseFirstArgumentWhenSecondIsAbsent() {
        // given
        final List<Data> source = givenOptableData("dataId1", "segmentId1", "field1", "value1");

        // when
        final List<com.iab.openrtb.request.Data> result = target.merge(null, source);

        // then
        assertThat(result).isNotNull()
                .hasSize(1);

        assertThat(result.getFirst())
                .returns("dataId1", from(com.iab.openrtb.request.Data::getId))
                .returns("segmentId1", it -> it.getSegment().getFirst().getId())
                .returns("value1", it -> it.getSegment().getFirst().getExt().get("field1").asText());
    }

    @Test
    public void shouldUseSecondArgumentWhenFirstIsAbsent() {
        // given
        final List<com.iab.openrtb.request.Data> destination = givenORTBData("dataId1", "segmentId1",
                "field1", "value1");
        // when
        final List<com.iab.openrtb.request.Data> result = target.merge(destination, null);

        // then
        assertThat(result).isNotNull()
                .hasSize(1);

        assertThat(result.getFirst())
                .returns("dataId1", from(com.iab.openrtb.request.Data::getId))
                .returns("segmentId1", it -> it.getSegment().getFirst().getId())
                .returns("value1", it -> it.getSegment().getFirst().getExt().get("field1").asText());
    }

    @Test
    public void shouldNotFailWhenArgumentsAreAbsent() {
        // given and when
        final List<com.iab.openrtb.request.Data> result = target.merge(null, null);

        // then
        assertThat(result).isNull();
    }

    private List<Data> givenOptableData(String id, String segmentId, String extField, String extValue) {
        return List.of(new Data(id, List.of(new Segment(segmentId, givenExt(Map.of(extField, extValue))))));
    }

    private List<com.iab.openrtb.request.Data> givenORTBData(String id, String segmentId, String extField,
                                                             String extValue) {

        return List.of(com.iab.openrtb.request.Data.builder()
                .id(id)
                .segment(List.of(com.iab.openrtb.request.Segment.builder()
                                .id(segmentId)
                                .ext(givenExt(Map.of(extField, extValue)))
                        .build()))
                .build());
    }
}
