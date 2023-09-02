package org.prebid.server.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jamsesso.jsonlogic.ast.JsonLogicNode;
import io.github.jamsesso.jsonlogic.ast.JsonLogicNull;
import io.github.jamsesso.jsonlogic.ast.JsonLogicParseException;
import io.github.jamsesso.jsonlogic.ast.JsonLogicString;
import io.github.jamsesso.jsonlogic.ast.JsonLogicVariable;
import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluationException;
import org.junit.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonLogicTest {

    private final ObjectMapper mapper = ObjectMapperProvider.mapper();
    private final JsonLogic jsonLogic = JsonLogicProvider.jsonLogic();

    @Test
    public void parseShouldReturnExpectedResult() throws JsonLogicParseException {
        // given
        final JsonNode jsonNode = mapper.valueToTree(Map.of("var", "a"));

        // when
        final JsonLogicNode jsonLogicNode = jsonLogic.parse(jsonNode);

        // then
        assertThat(jsonLogicNode).isInstanceOf(JsonLogicVariable.class);
    }

    @Test
    public void evaluateShouldReturnBoolean() throws JsonLogicEvaluationException {
        // given
        final JsonLogicNode jsonLogicNode = new JsonLogicVariable(new JsonLogicString("a"), JsonLogicNull.NULL);
        final Map<String, Object> data = Map.of("a", false);

        // when
        final boolean result = jsonLogic.evaluate(jsonLogicNode, data);

        // then
        assertThat(result).isFalse();
    }
}
