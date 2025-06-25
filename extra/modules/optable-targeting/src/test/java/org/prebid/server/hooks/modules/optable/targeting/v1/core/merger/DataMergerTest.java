package org.prebid.server.hooks.modules.optable.targeting.v1.core.merger;

import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Segment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;

public class DataMergerTest extends BaseMergerTest {

    @Test
    public void shouldMergeDifferentData() {
        // given
        final List<Data> destination = givenORTBData("dataId1", "segmentId1",
                "field1", "value1");
        final List<Data> source = givenOptableData("dataId2", "segmentId2", "field2", "value2");

        // when
        final List<Data> result = DataMerger.merge(destination, source);

        // then
        assertThat(result).isNotNull()
                .hasSize(2);

        assertThat(result.getFirst())
                .returns("dataId2", from(Data::getId))
                .returns("segmentId2", it -> it.getSegment().getFirst().getId())
                .returns("value2", it -> it.getSegment().getFirst().getExt().get("field2").asText());

        assertThat(result.get(1))
                .returns("dataId1", from(Data::getId))
                .returns("segmentId1", it -> it.getSegment().getFirst().getId())
                .returns("value1", it -> it.getSegment().getFirst().getExt().get("field1").asText());
    }

    @Test
    public void shouldMergeSegmentsWithinTheSameData() {
        // given
        final List<Data> destination = givenORTBData("dataId1", "segmentId1",
                "field1", "value1");
        final List<Data> source = givenOptableData("dataId1", "segmentId2", "field2", "value2");

        // when
        final List<Data> result = DataMerger.merge(destination, source);

        // then
        assertThat(result).isNotNull()
                .hasSize(1);

        assertThat(result.getFirst())
                .returns("dataId1", from(Data::getId))
                .returns("segmentId1", it -> it.getSegment().getFirst().getId())
                .returns("value1", it -> it.getSegment().getFirst().getExt().get("field1").asText())
                .returns("segmentId2", it -> it.getSegment().get(1).getId())
                .returns("value2", it -> it.getSegment().get(1).getExt().get("field2").asText());
    }

    @Test
    public void shouldMergeExtWithinTheSameSegment() {
        // given
        final List<Data> destination = givenORTBData("dataId1", "segmentId1",
                "field1", "value1");
        final List<Data> source = givenOptableData("dataId1", "segmentId1", "field2", "value2");

        // when
        final List<Data> result = DataMerger.merge(destination, source);

        // then
        assertThat(result).isNotNull()
                .hasSize(1);

        assertThat(result.getFirst())
                .returns("dataId1", from(Data::getId))
                .returns("segmentId1", it -> it.getSegment().getFirst().getId())
                .returns("value1", it -> it.getSegment().getFirst().getExt().get("field1").asText())
                .returns("value2", it -> it.getSegment().getFirst().getExt().get("field2").asText());
    }

    @Test
    public void shouldUseFirstArgumentWhenSecondIsAbsent() {
        // given
        final List<Data> source = givenOptableData("dataId1", "segmentId1", "field1", "value1");

        // when
        final List<Data> result = DataMerger.merge(null, source);

        // then
        assertThat(result).isNotNull()
                .hasSize(1);

        assertThat(result.getFirst())
                .returns("dataId1", from(Data::getId))
                .returns("segmentId1", it -> it.getSegment().getFirst().getId())
                .returns("value1", it -> it.getSegment().getFirst().getExt().get("field1").asText());
    }

    @Test
    public void shouldUseSecondArgumentWhenFirstIsAbsent() {
        // given
        final List<Data> destination = givenORTBData("dataId1", "segmentId1",
                "field1", "value1");
        // when
        final List<Data> result = DataMerger.merge(destination, null);

        // then
        assertThat(result).isNotNull()
                .hasSize(1);

        assertThat(result.getFirst())
                .returns("dataId1", from(Data::getId))
                .returns("segmentId1", it -> it.getSegment().getFirst().getId())
                .returns("value1", it -> it.getSegment().getFirst().getExt().get("field1").asText());
    }

    @Test
    public void shouldNotFailWhenArgumentsAreAbsent() {
        // given and when
        final List<Data> result = DataMerger.merge(null, null);

        // then
        assertThat(result).isNull();
    }

    private List<Data> givenOptableData(String id, String segmentId, String extField, String extValue) {
        return List.of(Data.builder()
                .id(id)
                .segment(List.of(Segment.builder()
                        .id(segmentId)
                        .ext(givenExt(Map.of(extField, extValue)))
                        .build()))
                .build());
    }

    private List<Data> givenORTBData(String id, String segmentId, String extField, String extValue) {

        return List.of(Data.builder()
                .id(id)
                .segment(List.of(Segment.builder()
                        .id(segmentId)
                        .ext(givenExt(Map.of(extField, extValue)))
                        .build()))
                .build());
    }
}
