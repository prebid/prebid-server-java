package org.prebid.server.hooks.modules.optable.targeting.v1.core.merger;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ExtMergerTest extends BaseMergerTest {

    @Test
    public void shouldMergeTwoExtObjects() {
        // given
        final ObjectNode destination = givenExt(Map.of("field1", "value1", "field2", "value2"));
        final ObjectNode source = givenExt(Map.of("field3", "value3", "field4", "value4"));

        // when
        final ObjectNode result = ExtMerger.mergeExt(destination, source);

        // then
        assertThat(result).isNotNull().hasSize(4);
        assertThat(result.get("field1").asText()).isEqualTo("value1");
        assertThat(result.get("field2").asText()).isEqualTo("value2");
        assertThat(result.get("field3").asText()).isEqualTo("value3");
        assertThat(result.get("field4").asText()).isEqualTo("value4");
    }

    @Test
    public void shouldUseFirstArgumentWhenSecondIsNull() {
        // given
        final ObjectNode destination = givenExt(Map.of("field1", "value1", "field2", "value2"));

        // when
        final ObjectNode result = ExtMerger.mergeExt(destination, null);

        // then
        assertThat(result).isNotNull().hasSize(2);
        assertThat(result.get("field1").asText()).isEqualTo("value1");
        assertThat(result.get("field2").asText()).isEqualTo("value2");
    }

    @Test
    public void shouldUseSecondArgumentWhenFirstIsNull() {
        // given
        final ObjectNode source = givenExt(Map.of("field1", "value1", "field2", "value2"));

        // when
        final ObjectNode result = ExtMerger.mergeExt(null, source);

        // then
        assertThat(result).isNotNull().hasSize(2);
        assertThat(result.get("field1").asText()).isEqualTo("value1");
        assertThat(result.get("field2").asText()).isEqualTo("value2");
    }

    @Test
    public void shouldNotFail() {
        // given and when
        final ObjectNode result = ExtMerger.mergeExt(null, null);

        // then
        assertThat(result).isNull();
    }
}
