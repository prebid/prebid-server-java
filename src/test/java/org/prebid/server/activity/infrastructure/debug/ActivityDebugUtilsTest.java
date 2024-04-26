package org.prebid.server.activity.infrastructure.debug;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;

import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class ActivityDebugUtilsTest extends VertxTest {

    @Mock
    private Loggable loggable;

    @Mock
    private Object object;

    @Test
    public void asLogEntryShouldReturnExpectedResult() {
        // given
        final ObjectNode objectNode = mapper.createObjectNode();
        given(loggable.asLogEntry(any())).willReturn(objectNode);

        // when
        final JsonNode result = ActivityDebugUtils.asLogEntry(loggable, mapper);

        // then
        assertThat(result).isSameAs(objectNode);
    }

    @Test
    public void asLogEntryShouldReturnTextNodeIfObjectDoesNotImplementLoggable() {
        // given
        given(object.toString()).willReturn("object");

        // when
        final JsonNode result = ActivityDebugUtils.asLogEntry(object, mapper);

        // then
        assertThat(result).isEqualTo(TextNode.valueOf("object"));
    }

    @Test
    public void asLogEntryShouldReturnArrayNodeOnList() {
        // given
        final ObjectNode objectNode = mapper.createObjectNode();
        given(loggable.asLogEntry(any())).willReturn(objectNode);

        given(object.toString()).willReturn("object");

        final List<?> objects = asList(loggable, object);

        // when
        final ArrayNode result = ActivityDebugUtils.asLogEntry(objects, mapper);

        // then
        assertThat(result).containsExactly(objectNode, TextNode.valueOf("object"));
    }
}
