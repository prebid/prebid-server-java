package org.prebid.server.hooks.modules.optable.targeting.v1.core.merger;

import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Uid;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;

public class EidsMergerTest extends BaseMergerTest {

    @Test
    public void shouldMergeDifferentEids() {
        // given
        final List<Eid> destination = givenEids("source1", "uid1", "field1", "value1");
        final List<Eid> source = givenEids("source2", "uid2", "field2", "value2");

        // when
        final List<Eid> result = EidsMerger.merge(destination, source);

        // then
        assertThat(result).isNotNull()
                .hasSize(2);

        assertThat(result.getFirst())
                .returns("source1", from(Eid::getSource))
                .returns("uid1", it -> it.getUids().getFirst().getId())
                .returns("value1", it -> it.getUids().getFirst().getExt().get("field1").asText());

        assertThat(result.get(1))
                .returns("source2", from(Eid::getSource))
                .returns("uid2", it -> it.getUids().getFirst().getId())
                .returns("value2", it -> it.getUids().getFirst().getExt().get("field2").asText());
    }

    @Test
    public void shouldMergeUidsWithinTheSameEid() {
        // given
        final List<Eid> destination = givenEids("source1", "uid1", "field1", "value1");
        final List<Eid> source = givenEids("source1", "uid2", "field2", "value2");

        // when
        final List<Eid> result = EidsMerger.merge(destination, source);

        // then
        assertThat(result).isNotNull()
                .hasSize(1);

        assertThat(result.getFirst())
                .returns("source1", from(Eid::getSource))
                .returns("uid2", it -> it.getUids().getFirst().getId())
                .returns("uid1", it -> it.getUids().get(1).getId())
                .returns("value2", it -> it.getUids().getFirst().getExt().get("field2").asText())
                .returns("value1", it -> it.getUids().get(1).getExt().get("field1").asText());
    }

    @Test
    public void shouldMergeExtWithinTheSameUid() {
        // given
        final List<Eid> destination = givenEids("source1", "uid1", "field1", "value1");
        final List<Eid> source = givenEids("source1", "uid1", "field2", "value2");

        // when
        final List<Eid> result = EidsMerger.merge(destination, source);

        // then
        assertThat(result).isNotNull()
                .hasSize(1);

        assertThat(result.getFirst())
                .returns("source1", from(Eid::getSource))
                .returns("uid1", it -> it.getUids().getFirst().getId())
                .returns("value2", it -> it.getUids().getFirst().getExt().get("field2").asText())
                .returns("value1", it -> it.getUids().getFirst().getExt().get("field1").asText());
    }

    @Test
    public void shouldUserFirstUidsListWhenSecondIsAbsent() {
        // given
        final List<Eid> destination = givenEids("source1", "uid1", "field1", "value1");

        // when
        final List<Eid> result = EidsMerger.merge(destination, null);

        // then
        assertThat(result).isNotNull()
                .hasSize(1);

        assertThat(result.getFirst())
                .returns("source1", from(Eid::getSource))
                .returns("uid1", it -> it.getUids().getFirst().getId())
                .returns("value1", it -> it.getUids().getFirst().getExt().get("field1").asText());
    }

    @Test
    public void shouldUserSecondUidsListWhenSecondIsEmpty() {
        // given
        final List<Eid> source = givenEids("source1", "uid1", "field1", "value1");

        // when
        final List<Eid> result = EidsMerger.merge(null, source);

        // then
        assertThat(result).isNotNull()
                .hasSize(1);

        assertThat(result.getFirst())
                .returns("source1", from(Eid::getSource))
                .returns("uid1", it -> it.getUids().getFirst().getId())
                .returns("value1", it -> it.getUids().getFirst().getExt().get("field1").asText());
    }

    private List<Eid> givenEids(String source, String uidId, String extField, String extValue) {
        return List.of(Eid.builder()
                .source(source)
                .uids(List.of(Uid.builder()
                        .id(uidId)
                        .ext(givenExt(Map.of(extField, extValue)))
                        .build()))
                .build());
    }
}
