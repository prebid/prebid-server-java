package org.prebid.server.json;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.jamsesso.jsonlogic.ast.JsonLogicNode;
import io.github.jamsesso.jsonlogic.ast.JsonLogicParseException;
import io.github.jamsesso.jsonlogic.ast.JsonLogicParser;
import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluationException;
import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluator;

import java.util.Map;
import java.util.Objects;

public class JsonLogic {

    private final JsonLogicEvaluator evaluator;

    public JsonLogic(JsonLogicEvaluator jsonLogicEvaluator) {
        evaluator = Objects.requireNonNull(jsonLogicEvaluator);
    }

    public JsonLogicNode parse(JsonNode jsonNode) throws JsonLogicParseException {
        return JsonLogicParser.parse(jsonNode.toString());
    }

    public boolean evaluate(JsonLogicNode jsonLogicNode, Map<String, Object> data) throws JsonLogicEvaluationException {
        final Object result = evaluator.evaluate(jsonLogicNode, data);

        if (result instanceof Boolean booleanResult) {
            return booleanResult;
        }
        throw new JsonLogicEvaluationException("Wrong type was returned.");
    }
}
